// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

/**
 * What the all-day strip draws once it is limited to a number of rows: the [bars] that stay
 * visible, how many events each day hides behind its "+N" cell ([hidden], one entry per day, zero
 * when nothing is hidden) and the [rows] the strip needs, the "+N" row included.
 */
data class AllDayStrip(val bars: List<AllDayBar>, val hidden: List<Int>, val rows: Int) {
    /** Whether any day has events behind a "+N" cell. */
    val hasOverflow: Boolean get() = hidden.any { it > 0 }
}

/** Limits the lanes of the all-day strip, as Google Calendar does in the Week view (RF-03). */
object AllDayLanes {
    /**
     * Fits the all-day bars of [page] in at most [maxRows] rows. When they all fit, nothing is
     * hidden. Otherwise the last row is given to the "+N" cells: the bars in that row and below
     * are hidden, and each day counts the hidden bars that cover it, so a bar spanning three days
     * adds one to each of the three.
     */
    fun limit(page: TimeGridPage, maxRows: Int): AllDayStrip {
        require(maxRows >= 1) { "The strip needs at least one row" }
        val needed = page.allDayRows
        if (needed <= maxRows) {
            return AllDayStrip(page.allDay, List(page.days.size) { 0 }, needed)
        }
        val (visible, hiddenBars) = page.allDay.partition { it.row < maxRows - 1 }
        val hidden = List(page.days.size) { day ->
            hiddenBars.count { day in it.firstDay..it.lastDay }
        }
        return AllDayStrip(visible, hidden, maxRows)
    }
}
