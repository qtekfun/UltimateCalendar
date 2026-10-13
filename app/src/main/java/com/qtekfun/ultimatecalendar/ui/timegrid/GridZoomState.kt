// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.qtekfun.ultimatecalendar.domain.timegrid.GridZoom
import kotlin.math.roundToInt

/** Below this distance between the fingers, in px, a pinch is too jittery to follow. */
private const val MIN_SPAN_PX = 24f

/**
 * The pinch zoom of the hour grid, shared by all its pages like the vertical scroll. [onSettled]
 * gets the zoom when a pinch ends, to keep it for the next launch.
 */
@Stable
internal class GridZoomState(initial: Float, private val onSettled: (Float) -> Unit) {
    var zoom by mutableFloatStateOf(GridZoom.clamp(initial))
        private set

    /** A scroll the grid could not reach yet because it had not grown to the new zoom. */
    var pendingScroll by mutableStateOf<Int?>(null)
        private set

    /** Zooms by [factor] keeping the time at [focus] px from the top of the grid in place. */
    fun pinch(factor: Float, focus: Float, scroll: ScrollState) {
        val from = zoom
        val to = GridZoom.clamp(from * factor)
        if (to == from) return
        zoom = to
        val target = GridZoom.scrollKeeping(scroll.value.toFloat(), focus, from, to).roundToInt()
        scroll.dispatchRawDelta((target - scroll.value).toFloat())
        pendingScroll = target.takeIf { it != scroll.value }
    }

    fun settle() = onSettled(zoom)

    /** Moves [scroll] to the pending target as soon as the grid is tall enough for it. */
    suspend fun followPending(scroll: ScrollState) {
        snapshotFlow { pendingScroll to scroll.maxValue }.collect { (target, max) ->
            if (target != null) {
                scroll.scrollTo(minOf(target, max))
                if (target <= max) pendingScroll = null
            }
        }
    }
}

/** The zoom of the grid being drawn; none in previews, which keep the plain size. */
internal val LocalGridZoom = staticCompositionLocalOf<GridZoomState?> { null }

/** Keeps [scroll] on the time under the fingers while the grid grows or shrinks. */
@Composable
internal fun FollowZoomScroll(zoom: GridZoomState?, scroll: ScrollState) {
    if (zoom != null) LaunchedEffect(zoom, scroll) { zoom.followPending(scroll) }
}

/**
 * Two fingers moving apart or together vertically zoom the hours. It looks at the touches before
 * the scroll, the pager and the event drag do, and takes them while two fingers are down so none
 * of those moves at the same time. One finger is left alone.
 */
internal fun Modifier.pinchToZoom(zoom: GridZoomState?, scroll: ScrollState): Modifier =
    if (zoom == null) {
        this
    } else {
        pointerInput(zoom, scroll) {
            awaitEachGesture {
                var pinched = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down = event.changes.filter { it.pressed }
                    if (down.size >= 2) {
                        pinched = true
                        val factor = event.verticalZoom()
                        val focus = down.map { it.position.y }.average().toFloat()
                        if (factor != 1f) zoom.pinch(factor, focus, scroll)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (pinched) zoom.settle()
            }
        }
    }

/** How much the vertical distance between the fingers changed since the last event. */
private fun PointerEvent.verticalZoom(): Float {
    val fingers = changes.filter { it.pressed && it.previousPressed }
    if (fingers.size < 2) return 1f
    val now = fingers.maxOf { it.position.y } - fingers.minOf { it.position.y }
    val before = fingers.maxOf { it.previousPosition.y } - fingers.minOf { it.previousPosition.y }
    return if (now < MIN_SPAN_PX || before < MIN_SPAN_PX) 1f else now / before
}
