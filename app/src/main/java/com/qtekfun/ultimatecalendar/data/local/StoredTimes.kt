// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.EventSeries
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** An [EventTime] as the columns of an event row: epoch milliseconds, or epoch days if all day. */
internal data class StoredTime(val start: Long, val end: Long, val zone: String?)

/** Times to columns and back, and the bounds of a series that the window query uses. */
internal object StoredTimes {
    /** The widest offset from UTC a zone can have either way; all-day windows grow by it. */
    private val ALL_DAY_MARGIN: Duration = Duration.ofHours(MARGIN_HOURS)

    fun columns(time: EventTime): StoredTime = when (time) {
        is EventTime.AllDay -> StoredTime(
            time.startDate.toEpochDay(),
            time.endDate.toEpochDay(),
            null
        )

        is EventTime.Timed -> StoredTime(
            time.start.toEpochMilli(),
            time.end.toEpochMilli(),
            time.zone.id
        )
    }

    fun time(allDay: Boolean, start: Long, end: Long, zone: String?): EventTime = if (allDay) {
        EventTime.AllDay(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(end))
    } else {
        EventTime.Timed(
            Instant.ofEpochMilli(start),
            Instant.ofEpochMilli(end),
            ZoneId.of(requireNotNull(zone))
        )
    }

    /**
     * Every occurrence of the series starts after the first bound and ends before the second.
     * A series that repeats or has `RDATE`s has no known end.
     */
    fun window(series: EventSeries): Pair<Long, Long?> {
        val master = series.event
        val open = master.rrule != null || series.rDates.isNotEmpty()
        val (start, end) = when (val time = master.time) {
            is EventTime.AllDay ->
                time.startDate.toEpochDay() * MILLIS_PER_DAY - ALL_DAY_MARGIN.toMillis() to
                    time.endDate.toEpochDay() * MILLIS_PER_DAY + ALL_DAY_MARGIN.toMillis()

            is EventTime.Timed -> time.start.toEpochMilli() to time.end.toEpochMilli()
        }
        return start to end.takeUnless { open }
    }

    private const val MARGIN_HOURS = 14L
    private const val MILLIS_PER_DAY = 86_400_000L
}
