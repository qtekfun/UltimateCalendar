// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

/**
 * Which event is under a point of the grid, so a long press can pick it up (T18). The grid code
 * measures the point in its own units and gives it here as fractions and minutes, which keeps
 * the rules (blocks drawn at least [TimeGridLayout.MIN_DURATION] tall, columns, the handle at
 * the bottom) testable without a screen.
 */
object GridHit {
    /**
     * The timed block at [x] (0 to 1 across all the day columns) and [minute] of the day, the
     * one drawn on top when blocks overlap, or null.
     */
    fun timedAt(page: TimeGridPage, x: Float, minute: Float): TimedBlock? {
        val days = page.days.size
        val scaled = x.coerceIn(0f, LAST_FRACTION) * days
        val day = scaled.toInt()
        val across = scaled - day
        return page.timed.lastOrNull { block ->
            block.dayIndex == day &&
                minute >= block.startMinute && minute < drawnEnd(block) &&
                across >= block.column.toFloat() / block.columns &&
                across < (block.column + block.span).toFloat() / block.columns
        }
    }

    /** The all-day bar over [day] (index in the page) in strip [row], or null. */
    fun allDayAt(bars: List<AllDayBar>, day: Int, row: Int): AllDayBar? =
        bars.lastOrNull { it.row == row && day in it.firstDay..it.lastDay }

    /**
     * Whether [minute] is on the handle of [block]: its last [handleMinutes] minutes, but never
     * more than half of it so that there is always somewhere left to grab it by. A block that
     * goes on to the next day has no handle here: its end is not on this day.
     */
    fun isOnHandle(block: TimedBlock, minute: Float, handleMinutes: Float): Boolean {
        if (block.continuesAfter) return false
        val end = drawnEnd(block)
        val zone = minOf(handleMinutes, (end - block.startMinute) / 2f)
        return minute >= end - zone
    }

    /** Where the block is drawn to: short events are drawn at least [TimeGridLayout.MIN_DURATION]. */
    private fun drawnEnd(block: TimedBlock): Float = maxOf(
        block.endMinute,
        block.startMinute + TimeGridLayout.MIN_DURATION.toMinutes().toInt()
    ).toFloat()

    private const val LAST_FRACTION = 0.99999f
}
