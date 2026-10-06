// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * When an event happens. All-day events are dates, not instants: they stay on the same days in
 * every time zone.
 */
sealed interface EventTime {
    /** A moment-to-moment event, shown in [zone] (the event's own zone, maybe not the phone's). */
    data class Timed(val start: Instant, val end: Instant, val zone: ZoneId) : EventTime {
        init {
            require(!end.isBefore(start)) { "An event cannot end before it starts" }
        }
    }

    /** Whole days from [startDate] up to, but not including, [endDate] (as iCalendar's `DTEND`). */
    data class AllDay(val startDate: LocalDate, val endDate: LocalDate) : EventTime {
        init {
            require(endDate.isAfter(startDate)) { "An all-day event lasts at least one day" }
        }

        /** The last day shown, inclusive. */
        val lastDate: LocalDate get() = endDate.minusDays(1)
    }

    /** The start as an instant; all-day events start at midnight of [zone]. */
    fun startIn(zone: ZoneId): Instant = when (this) {
        is Timed -> start
        is AllDay -> startDate.atStartOfDay(zone).toInstant()
    }

    /** The end as an instant; all-day events end at midnight of [zone]. */
    fun endIn(zone: ZoneId): Instant = when (this) {
        is Timed -> end
        is AllDay -> endDate.atStartOfDay(zone).toInstant()
    }
}
