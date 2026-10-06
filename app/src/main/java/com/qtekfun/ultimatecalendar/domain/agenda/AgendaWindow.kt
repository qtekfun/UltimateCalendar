// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.agenda

import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import java.time.LocalDate

/**
 * The days the agenda has loaded around an [anchor] (the date it was asked to show). The agenda
 * scrolls without end in both directions by growing the window a chunk at a time, up to
 * [MAX_SPAN_DAYS] either side of the anchor; a jump to another date starts a new window.
 */
data class AgendaWindow(val anchor: LocalDate, val range: DateRange) {
    val canExtendEarlier: Boolean get() = range.start.isAfter(anchor.minusDays(MAX_SPAN_DAYS))

    val canExtendLater: Boolean get() = range.endExclusive.isBefore(anchor.plusDays(MAX_SPAN_DAYS))

    /** The window with a chunk more before it, or the same one at the limit. */
    fun earlier(): AgendaWindow {
        val start = maxOf(range.start.minusDays(CHUNK_DAYS), anchor.minusDays(MAX_SPAN_DAYS))
        return copy(range = DateRange(start, range.endExclusive))
    }

    /** The window with a chunk more after it, or the same one at the limit. */
    fun later(): AgendaWindow {
        val end = minOf(range.endExclusive.plusDays(CHUNK_DAYS), anchor.plusDays(MAX_SPAN_DAYS))
        return copy(range = DateRange(range.start, end))
    }

    operator fun contains(date: LocalDate): Boolean = date in range

    companion object {
        const val CHUNK_DAYS = 30L
        const val DAYS_BEFORE = 14L
        const val DAYS_AFTER = 30L
        const val MAX_SPAN_DAYS = 366L

        /** The first window for [anchor]: a little before it and a month after. */
        fun around(anchor: LocalDate) = AgendaWindow(
            anchor,
            DateRange(anchor.minusDays(DAYS_BEFORE), anchor.plusDays(DAYS_AFTER))
        )
    }
}
