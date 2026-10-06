// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.ZoneId

/** The occurrence a search result stands for, and whether it is still to come. */
data class ChosenOccurrence(val instance: EventInstance, val upcoming: Boolean)

/**
 * Chooses the one occurrence of an event that a search result shows: the next one that has not
 * ended yet (one in progress counts), or, when the event is over, the latest one. A series is
 * listed once, whatever the number of occurrences.
 */
object OccurrencePicker {
    /**
     * Picks among [occurrences]; when the source knows none, [fallback] (the series' own first
     * occurrence) is the only candidate.
     */
    fun pick(
        now: Instant,
        zone: ZoneId,
        occurrences: List<EventInstance>,
        fallback: EventInstance
    ): ChosenOccurrence {
        val candidates = occurrences.ifEmpty { listOf(fallback) }
        val (coming, over) = candidates.partition { !hasEnded(it.time, now, zone) }
        return if (coming.isNotEmpty()) {
            ChosenOccurrence(coming.minBy { it.time.startIn(zone) }, upcoming = true)
        } else {
            ChosenOccurrence(over.maxBy { it.time.startIn(zone) }, upcoming = false)
        }
    }

    /** All-day events end with their last day, in [zone]; a moment-long event is over after it. */
    fun hasEnded(time: EventTime, now: Instant, zone: ZoneId): Boolean = when (time) {
        is EventTime.AllDay -> !time.endDate.isAfter(now.atZone(zone).toLocalDate())
        is EventTime.Timed -> time.end.isBefore(now)
    }
}
