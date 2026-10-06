// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Events
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Time columns of events. The provider keeps all-day events as UTC midnights with the zone
 * "UTC", and repeating events with a `DURATION` instead of a `DTEND`.
 */
internal object EventTimeMapping {
    private const val MILLIS = 1_000L
    private const val SECONDS_PER_DAY = 86_400L
    private const val SECONDS_PER_WEEK = 7 * SECONDS_PER_DAY
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_MINUTE = 60L

    /** The zone the provider names "UTC" (not the offset `Z`). */
    private val utc: ZoneId = ZoneId.of("UTC")
    private val durationPart = Regex("""(\d+)([WDHMS])""")

    /** The zone of [id], or UTC when the provider has none or an unknown one. */
    fun zoneOf(id: String?): ZoneId = try {
        if (id.isNullOrBlank()) utc else ZoneId.of(id)
    } catch (_: DateTimeException) {
        utc
    }

    /**
     * The time of an event or instance from its columns. [endMs] is the end, when the row has
     * one; otherwise the end is the start plus [duration] (RFC 2445 text), or the start itself.
     * An end before the start is read as the start, so foreign data never breaks the model.
     */
    fun read(
        allDay: Boolean,
        startMs: Long,
        endMs: Long?,
        duration: String?,
        timeZone: String?
    ): EventTime {
        val end = endMs ?: (startMs + (parseDurationSeconds(duration) ?: 0L) * MILLIS)
        return if (allDay) {
            val first = utcDate(startMs)
            // End (exclusive) or last millisecond of the last day: both give the day after it.
            val last = utcDate((end - 1).coerceAtLeast(startMs))
            EventTime.AllDay(first, last.plusDays(1))
        } else {
            val start = Instant.ofEpochMilli(startMs)
            EventTime.Timed(
                start,
                Instant.ofEpochMilli(end).let { if (it.isBefore(start)) start else it },
                zoneOf(timeZone)
            )
        }
    }

    /**
     * The columns that store [time]. A series ([repeats]) takes `DURATION` and no `DTEND`, as
     * the provider requires; a single event the opposite.
     */
    fun write(time: EventTime, repeats: Boolean): ProviderRow {
        val columns = when (time) {
            is EventTime.AllDay -> Columns(
                true,
                time.startDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                time.endDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                utc
            )

            is EventTime.Timed -> Columns(false, time.start, time.end, time.zone)
        }
        val allDay = columns.allDay
        val seconds = Duration.between(columns.start, columns.end).seconds
        return mapOf(
            Events.ALL_DAY to if (allDay) 1 else 0,
            Events.DTSTART to columns.start.toEpochMilli(),
            Events.DTEND to if (repeats) null else columns.end.toEpochMilli(),
            Events.DURATION to if (repeats) formatDuration(seconds, allDay) else null,
            Events.EVENT_TIMEZONE to columns.zone.id
        )
    }

    /** `P1D` for whole days, `P3600S` otherwise (what the system Calendar app writes). */
    fun formatDuration(seconds: Long, allDay: Boolean): String =
        if (allDay) "P${seconds / SECONDS_PER_DAY}D" else "P${seconds}S"

    /** Seconds of an RFC 2445 duration such as `P3600S`, `PT1H30M`, `P1D` or `P2W`, or null. */
    fun parseDurationSeconds(text: String?): Long? {
        val trimmed = text?.trim()?.removePrefix("+").orEmpty()
        val body = trimmed.removePrefix("P").replace("T", "")
        val parts = durationPart.findAll(body).toList()
        val whole = trimmed.startsWith("P") && parts.joinToString("") { it.value } == body
        return if (whole && parts.isNotEmpty()) parts.sumOf { secondsOf(it) } else null
    }

    private fun secondsOf(part: MatchResult): Long {
        val amount = part.groupValues[1].toLong()
        return amount * when (part.groupValues[2]) {
            "W" -> SECONDS_PER_WEEK
            "D" -> SECONDS_PER_DAY
            "H" -> SECONDS_PER_HOUR
            "M" -> SECONDS_PER_MINUTE
            else -> 1L
        }
    }

    private fun utcDate(ms: Long): LocalDate =
        Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

    private data class Columns(
        val allDay: Boolean,
        val start: Instant,
        val end: Instant,
        val zone: ZoneId
    )
}
