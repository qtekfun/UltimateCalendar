// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.boundsInWindow
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.timegrid.EdgePaging
import com.qtekfun.ultimatecalendar.domain.timegrid.EdgeScroll
import com.qtekfun.ultimatecalendar.domain.timegrid.PageEdge

private const val EDGE_ZONE_DP = 72f
private const val SIDE_ZONE_DP = 40f
private const val SCROLL_DP_PER_SECOND = 720f
private const val PAGE_DELAY_MS = 700L
private const val NANOS_PER_MILLI = 1_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000f

/** How fast to scroll the hours, in px per second (negative: up), for the finger where it is. */
internal fun DragController.verticalSpeed(): Float {
    val timed = session?.original is EventTime.Timed
    val bounds = current()?.viewport?.takeIf { it.isAttached }?.boundsInWindow()
    return if (!timed || bounds == null) {
        0f
    } else {
        EdgeScroll.vertical(
            pointer.y,
            bounds.top,
            bounds.bottom,
            EDGE_ZONE_DP * density,
            SCROLL_DP_PER_SECOND * density
        )
    }
}

/** The side of the screen the finger is at, which pushes towards the neighbouring page. */
internal fun DragController.horizontalEdge(): PageEdge {
    val bounds = host?.takeIf { it.isAttached }?.boundsInWindow() ?: return PageEdge.NONE
    return EdgeScroll.horizontal(pointer.x, bounds.left, bounds.right, SIDE_ZONE_DP * density)
}

/**
 * While an event is held: scrolls the hours when the finger is near the top or bottom, and at
 * the side scrolls the columns sideways if they can, else turns to the next day or week after a
 * short wait. Each frame it works out again where the event would go, because the grid moved
 * under a finger that did not.
 */
@Composable
internal fun DragEdgeEffect(
    controller: DragController,
    pager: PagerState,
    scroll: ScrollState,
    reduceMotion: Boolean
) {
    val active = controller.active
    LaunchedEffect(controller, active) {
        val paging = EdgePaging(PAGE_DELAY_MS)
        var last = withFrameNanos { it }
        while (controller.active) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / NANOS_PER_SECOND
            last = now
            val speed = controller.verticalSpeed()
            if (speed != 0f) scroll.scrollBy(speed * seconds)
            val edge = controller.horizontalEdge()
            val sideways = controller.sidewaysScroll(edge, seconds)
            val turn = paging.onPointer(
                if (sideways) PageEdge.NONE else edge,
                now / NANOS_PER_MILLI
            )
            if (turn != PageEdge.NONE) pager.turn(turn, reduceMotion)
            controller.refresh()
        }
    }
}

/** Scrolls the columns towards [edge] if they can; true when they did. */
private suspend fun DragController.sidewaysScroll(edge: PageEdge, seconds: Float): Boolean {
    val scroll = current()?.sideways
    val forward = edge == PageEdge.NEXT && scroll?.canScrollForward == true
    val backward = edge == PageEdge.PREVIOUS && scroll?.canScrollBackward == true
    if (scroll == null || !(forward || backward)) return false
    val direction = if (forward) 1f else -1f
    scroll.scrollBy(direction * SCROLL_DP_PER_SECOND * density * seconds)
    return true
}

private suspend fun PagerState.turn(edge: PageEdge, reduceMotion: Boolean) {
    val step = if (edge == PageEdge.NEXT) 1 else -1
    val target = (currentPage + step).coerceIn(0, pageCount - 1)
    if (reduceMotion) scrollToPage(target) else animateScrollToPage(target)
}
