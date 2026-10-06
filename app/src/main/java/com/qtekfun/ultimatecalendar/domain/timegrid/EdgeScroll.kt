// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

/** Which neighbouring page a dragging finger is pushing towards. */
enum class PageEdge { NONE, PREVIOUS, NEXT }

/**
 * What happens when a finger holding an event reaches the edge of the grid (T18): the hours
 * scroll, and holding it at the side turns to the neighbouring day or week.
 */
object EdgeScroll {
    /**
     * How fast to scroll, in the unit of the arguments per second, when a finger is [distance]
     * from the edge it is heading to (negative when past it). Nothing outside [zone] from the
     * edge; linearly faster up to [maxSpeed] at the edge and beyond.
     */
    fun speed(distance: Float, zone: Float, maxSpeed: Float): Float {
        require(zone > 0f) { "The edge zone needs some size" }
        val depth = ((zone - distance) / zone).coerceIn(0f, 1f)
        return depth * maxSpeed
    }

    /**
     * The vertical speed (negative scrolls up) of a finger at [y] in a viewport from [top] to
     * [bottom]; zero in the middle.
     */
    fun vertical(y: Float, top: Float, bottom: Float, zone: Float, maxSpeed: Float): Float {
        val up = speed(y - top, zone, maxSpeed)
        val down = speed(bottom - y, zone, maxSpeed)
        return if (up > down) -up else down
    }

    /** The page a finger at [x] in a viewport from [left] to [right] pushes towards. */
    fun horizontal(x: Float, left: Float, right: Float, zone: Float): PageEdge = when {
        x - left < zone -> PageEdge.PREVIOUS
        right - x < zone -> PageEdge.NEXT
        else -> PageEdge.NONE
    }
}

/**
 * Decides when holding a finger at the side turns the page: only after it has stayed there for
 * [delayMillis], and then again after another [delayMillis] if it is still there, so that a
 * finger brushing past the edge does not flip pages.
 */
class EdgePaging(private val delayMillis: Long) {
    private var edge = PageEdge.NONE
    private var since = 0L

    /**
     * Feed it the edge the finger is at, [now] in milliseconds. Answers the edge to turn the
     * page towards, or [PageEdge.NONE] while there is nothing to do yet.
     */
    fun onPointer(at: PageEdge, now: Long): PageEdge {
        val arrived = at != edge
        if (arrived) {
            edge = at
            since = now
        }
        val due = !arrived && at != PageEdge.NONE && now - since >= delayMillis
        if (due) since = now
        return if (due) at else PageEdge.NONE
    }
}
