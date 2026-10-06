// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Long press an event, then drag it (T18). It looks at the touches before the events and the
 * pager do (the initial pass) but only takes one over once the press has lasted: a tap, a
 * scroll and a swipe between pages are left alone. From then on it consumes the finger, so
 * nothing else scrolls or opens the event, until it lets go.
 */
internal fun Modifier.dragGestures(controller: DragController): Modifier =
    onGloballyPositioned { controller.host = it }.pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val candidate = controller.candidateAt(controller.toWindow(down.position))
            if (candidate != null && awaitLongPress(down.id, down.position)) {
                val window = controller.toWindow(down.position)
                controller.pickUp(candidate, window)
                holdUntilUp(down.id, controller)
            }
        }
    }

private fun DragController.toWindow(local: Offset): Offset =
    host?.takeIf { it.isAttached }?.localToWindow(local) ?: local

/** True when the finger stayed where it was until the long press time passed. */
private suspend fun AwaitPointerEventScope.awaitLongPress(id: PointerId, from: Offset): Boolean {
    var moved = false
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (!moved) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                .firstOrNull { it.id == id }
            moved = change == null || !change.pressed || change.isConsumed ||
                (change.position - from).getDistance() > viewConfiguration.touchSlop
        }
    }
    return !moved
}

/** Follows the finger and keeps it to itself, until it lets go (or the system takes over). */
private suspend fun AwaitPointerEventScope.holdUntilUp(id: PointerId, controller: DragController) {
    try {
        var held = true
        while (held) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                .firstOrNull { it.id == id }
            held = change != null && change.pressed
            change?.consume()
            when {
                change == null -> Unit
                held -> controller.move(controller.toWindow(change.position))
                else -> controller.release()
            }
        }
    } finally {
        controller.cancel()
    }
}
