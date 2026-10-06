// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

/**
 * The vertical scale of a day grid: [hourHeight] units (dp, px: the caller's choice) per hour.
 * The grid is a wall-clock day of 24 hours in every zone, even on the 23 and 25 hour days of a
 * clock change, so the columns of Day, 3 days and Week line up (see [TimeGridLayout]).
 */
class TimeScale(val hourHeight: Float) {
    init {
        require(hourHeight > 0f) { "An hour must have some height" }
    }

    val totalHeight: Float get() = offsetOf(MINUTES_PER_DAY)

    /** The distance from the top of the grid to [minute] of the day (0 to 1440). */
    fun offsetOf(minute: Int): Float = minute * hourHeight / MINUTES_PER_HOUR

    /** The height of a block from [startMinute] to [endMinute], never less than [minHeight]. */
    fun heightOf(startMinute: Int, endMinute: Int, minHeight: Float = 0f): Float =
        maxOf(offsetOf(endMinute) - offsetOf(startMinute), minHeight)

    /** How many minutes a distance of [distance] (negative: upwards) on the scale stands for. */
    fun minutesOf(distance: Float): Float = distance * MINUTES_PER_HOUR / hourHeight

    /**
     * The minute under [offset] from the top of the grid, rounded down to a multiple of
     * [snapMinutes] and kept inside the day: a tap on an empty slot starts an event there, and
     * dragging (T18) moves events in the same steps.
     */
    fun minuteAt(offset: Float, snapMinutes: Int = DEFAULT_SNAP_MINUTES): Int {
        require(snapMinutes in 1..MINUTES_PER_HOUR) { "Snap must be within an hour" }
        val raw = (offset * MINUTES_PER_HOUR / hourHeight).toInt()
        return raw.coerceIn(0, MINUTES_PER_DAY - 1) / snapMinutes * snapMinutes
    }

    companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
        const val DEFAULT_SNAP_MINUTES = 30
    }
}
