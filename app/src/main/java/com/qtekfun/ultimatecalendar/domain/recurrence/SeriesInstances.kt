// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import java.time.ZoneId

/** Turns the occurrences of a series into the instances of a range, applying EXDATE and overrides. */
internal object SeriesInstances {
    fun assemble(
        series: EventSeries,
        times: List<EventTime>,
        range: TimeRange,
        zone: ZoneId
    ): List<EventInstance> {
        val replaced = series.overrides.map { it.recurrenceId }.toSet()
        val own = times.filter { it.key() !in series.exDates && it.key() !in replaced }
            .map { instance(series, series.event, it) }
        val edited = series.overrides.mapNotNull { override ->
            override.replacement?.let { instance(series, it, it.time) }
        }
        return (own + edited).filter { touches(it.time, range, zone) }
            .sortedWith(compareBy({ it.time.startIn(zone) }, { it.eventId.value }))
    }

    private fun instance(series: EventSeries, event: Event, time: EventTime) = EventInstance(
        eventId = event.id,
        calendarId = event.calendarId,
        title = event.title,
        time = time,
        location = event.location,
        color = event.color,
        isRecurring = series.event.isRecurring || series.rDates.isNotEmpty()
    )

    /** Whether [time] overlaps [range]; an instant event counts when it falls inside. */
    private fun touches(time: EventTime, range: TimeRange, zone: ZoneId): Boolean {
        val start = time.startIn(zone)
        val end = time.endIn(zone)
        val overlaps = if (end == start) !start.isBefore(range.start) else end.isAfter(range.start)
        return start.isBefore(range.end) && overlaps
    }
}
