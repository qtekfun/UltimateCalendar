// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.month

/**
 * What a week row shows when only some lanes fit: the [visible] bars, and per column how many
 * events hide behind its "+N more" ([more], 0 for none). The "+N more" takes the last lane of
 * its column, so a column that overflows shows one event less than the lanes it has.
 */
data class WeekFit(val visible: List<MonthBar>, val more: List<Int>)

/** The "+N more" arithmetic of a month row (RF-03). Pure. */
object MonthOverflow {
    fun fit(week: MonthWeek, lanes: Int): WeekFit {
        val capacity = lanes.coerceAtLeast(0)
        var hidden = week.bars.filter { it.lane >= capacity }.toSet()
        var more = countHidden(week, hidden)
        while (capacity > 0) {
            // A column with hidden events gives its last lane to "+N more": whatever is there
            // goes behind it too, which may overflow more columns.
            val reserved = capacity - 1
            val squeezed = week.bars.filter { bar ->
                bar !in hidden && bar.lane == reserved &&
                    (bar.firstCol..bar.lastCol).any { more[it] > 0 }
            }
            if (squeezed.isEmpty()) break
            hidden = hidden + squeezed
            more = countHidden(week, hidden)
        }
        return WeekFit(week.bars.filter { it !in hidden }, more)
    }

    private fun countHidden(week: MonthWeek, hidden: Set<MonthBar>): List<Int> =
        List(week.days.size) { col -> hidden.count { col in it } }
}
