// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import com.qtekfun.ultimatecalendar.domain.timegrid.AllDayBar
import com.qtekfun.ultimatecalendar.domain.timegrid.GridHit
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridPage
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeScale
import com.qtekfun.ultimatecalendar.domain.timegrid.TimedBlock
import java.time.ZoneId
import java.time.ZoneOffset

/** A point of the day columns, for the drag: the [day] (index in the page) and [y] in dp. */
internal data class GridPoint(val day: Int, val y: Float)

/**
 * Where a page of the grid is on the screen, as its composables last reported it (T18). The
 * gesture reads it to find what is under a finger; none of it is state to draw from.
 */
internal class PageSurface {
    var page: TimeGridPage = TimeGridPage(emptyList())
    var scale: TimeScale = TimeScale(1f)
    var zone: ZoneId = ZoneOffset.UTC
    var sideways: ScrollState? = null

    /** The visible area of the hour grid, without what has scrolled out of it. */
    var viewport: LayoutCoordinates? = null

    /** The day columns, all of them: as tall as the day and as wide as the days need. */
    var columns: LayoutCoordinates? = null

    var stripViewport: LayoutCoordinates? = null
    var strip: LayoutCoordinates? = null
    var stripBars: List<AllDayBar> = emptyList()

    /** The timed event under [window], and whether it is its handle that is touched. */
    fun timedAt(window: Offset, density: Float): DragCandidate.Timed? {
        val view = viewport?.takeIf { it.isAttached }
        val grid = columns?.takeIf { it.isAttached }
        if (view == null || grid == null) return null
        val local = grid.windowToLocal(window)
        val width = grid.size.width.toFloat()
        val inside = view.boundsInWindow().contains(window) && local.x in 0f..width
        val minute = scale.minutesOf(local.y / density)
        val block: TimedBlock? = GridHit.timedAt(page, local.x / width, minute)
        return if (inside &&
            block != null
        ) {
            DragCandidate.Timed(block, kindOf(block, minute))
        } else {
            null
        }
    }

    private fun kindOf(block: TimedBlock, minute: Float): DragKind =
        if (GridHit.isOnHandle(block, minute, scale.minutesOf(HANDLE_DP))) {
            DragKind.RESIZE
        } else {
            DragKind.MOVE
        }

    /** The all-day event under [window]. */
    fun allDayAt(window: Offset, density: Float): DragCandidate.AllDay? {
        val view = stripViewport?.takeIf { it.isAttached }
        val inner = strip?.takeIf { it.isAttached }
        if (view == null || inner == null) return null
        val local = inner.windowToLocal(window)
        val width = inner.size.width.toFloat()
        val inside = view.boundsInWindow().contains(window) && local.x in 0f..width
        val day = (local.x / width * page.days.size).toInt().coerceIn(0, page.days.size - 1)
        val row = (local.y / (ALL_DAY_ROW.value * density)).toInt()
        val bar = if (inside) GridHit.allDayAt(stripBars, day, row) else null
        return bar?.let { DragCandidate.AllDay(it) }
    }

    /** Where [window] is over the day columns, also when it is outside them. */
    fun pointAt(window: Offset, density: Float): GridPoint? {
        val grid = columns?.takeIf { it.isAttached } ?: return null
        val local = grid.windowToLocal(window)
        val day = (local.x / grid.size.width * page.days.size).toInt()
        return GridPoint(day.coerceIn(0, page.days.size - 1), local.y / density)
    }

    private companion object {
        /** The size of the handle to grab; a block never gives more than half of itself. */
        const val HANDLE_DP = 48f
    }
}
