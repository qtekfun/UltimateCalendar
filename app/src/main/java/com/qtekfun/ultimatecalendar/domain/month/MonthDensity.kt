// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.month

/** How much a month cell can hold, from its size; the cell falls back to dots when too small. */
object MonthDensity {
    /** Fewer lanes than this and a cell shows dots: one chip plus a "+N more" says little. */
    const val MIN_CHIP_LANES = 2

    /** How many lanes fit in a cell of [cellHeight] under a [headerHeight] (any one unit). */
    fun lanes(cellHeight: Float, headerHeight: Float, laneHeight: Float): Int {
        require(laneHeight > 0f) { "A lane has some height" }
        return ((cellHeight - headerHeight) / laneHeight).toInt().coerceAtLeast(0)
    }

    fun isCompact(lanes: Int): Boolean = lanes < MIN_CHIP_LANES
}
