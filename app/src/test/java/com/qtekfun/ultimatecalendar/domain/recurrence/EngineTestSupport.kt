// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertTrue

internal val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
internal val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

internal fun timed(local: String, minutes: Long = 60, zone: ZoneId = MADRID): EventTime.Timed {
    val start = LocalDateTime.parse(local).atZone(zone).toInstant()
    return EventTime.Timed(start, start.plusSeconds(minutes * 60), zone)
}

internal fun allDay(start: String, days: Long = 1): EventTime.AllDay =
    LocalDate.parse(start).let { EventTime.AllDay(it, it.plusDays(days)) }

internal fun event(time: EventTime, rrule: String?, id: Long = 1, title: String = "Gym") =
    Event(EventId(id), CalendarId(1), title, time, rrule = rrule)

/** A range of whole UTC days, [from] inclusive and [to] exclusive. */
internal fun days(from: String, to: String) = TimeRange(
    LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant(),
    LocalDate.parse(to).atStartOfDay(ZoneOffset.UTC).toInstant()
)

internal fun instants(from: String, to: String) = TimeRange(Instant.parse(from), Instant.parse(to))

/** The start of each instance: a date for all-day ones, a local date-time in [zone] otherwise. */
internal fun Expansion.starts(zone: ZoneId = MADRID): List<String> = instances().map {
    when (val time = it.time) {
        is EventTime.AllDay -> time.startDate.toString()
        is EventTime.Timed -> time.start.atZone(zone).toLocalDateTime().toString()
    }
}

internal fun Expansion.instances(): List<EventInstance> {
    assertTrue(this is Expansion.Complete, "Expected a complete expansion but got $this")
    return (this as Expansion.Complete).instances
}

internal fun expand(
    time: EventTime,
    rrule: String?,
    range: TimeRange,
    exDates: Set<OccurrenceKey> = emptySet(),
    rDates: Set<OccurrenceKey> = emptySet(),
    overrides: List<OccurrenceOverride> = emptyList(),
    zone: ZoneId = ZoneOffset.UTC
): Expansion = RecurrenceEngine.expand(
    EventSeries(event(time, rrule), exDates, rDates, overrides),
    range,
    zone
)

internal fun day(date: String) = OccurrenceKey.Day(LocalDate.parse(date))

internal fun moment(local: String, zone: ZoneId = MADRID) =
    OccurrenceKey.Moment(LocalDateTime.parse(local).atZone(zone).toInstant())
