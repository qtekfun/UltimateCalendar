// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

/**
 * The vertical zoom of the hour grid (Day, 3 days and Week), changed with a pinch: a factor on
 * the height of an hour, between [MIN] (the whole day nearly fits on a phone) and [MAX].
 */
object GridZoom {
    const val MIN = 0.5f
    const val MAX = 2.5f
    const val DEFAULT = 1f

    /** [zoom] brought into [MIN]..[MAX]; anything that is not a number is [DEFAULT]. */
    fun clamp(zoom: Float): Float = if (zoom.isFinite()) zoom.coerceIn(MIN, MAX) else DEFAULT

    /**
     * The scroll that keeps the time under the fingers in place when the zoom goes from [from]
     * to [to]: [scroll] is the current scroll and [focus] where the fingers are, from the top of
     * the visible grid, both in the same unit. Never above the top of the grid.
     */
    fun scrollKeeping(scroll: Float, focus: Float, from: Float, to: Float): Float =
        ((scroll + focus) * to / from - focus).coerceAtLeast(0f)
}
