// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.timegrid.DayBounds
import com.qtekfun.ultimatecalendar.domain.timegrid.EventDrag
import java.time.ZoneId

/**
 * The state of dragging an event on a Day, 3 days or Week grid (RF-03, T18). It lives above the
 * pager so that the drag goes on when the grid turns to the next day or week. The pages report
 * where they are ([surface]); the gesture feeds it the finger ([candidateAt], [pickUp], [move],
 * [release]); what it decides is [session], which the pages draw and [editing] saves.
 */
@Stable
internal class DragController {
    /** The event being held and where it would go, or null when nothing is held. */
    var session by mutableStateOf<DragSession?>(null)
        private set

    val active: Boolean get() = session != null

    var editing: GridEditing = GridEditing()

    /** True while a change is being saved: nothing else can be picked up meanwhile. */
    var busy: Boolean = false

    var density: Float = 1f
    var host: LayoutCoordinates? = null
    var currentPage: () -> Int = { 0 }
    var haptic: (HapticFeedbackType) -> Unit = {}

    /** The finger, in window coordinates. */
    var pointer: Offset = Offset.Zero
        private set

    private val surfaces = HashMap<Int, PageSurface>()
    private var grabDay = 0L
    private var grabY = 0f

    /** The surface of page [index] of the pager, made when first asked for. */
    fun surface(index: Int): PageSurface = surfaces.getOrPut(index) { PageSurface() }

    /** The surface the finger is over: the page on screen. */
    fun current(): PageSurface? = surfaces[currentPage()]

    fun forget(index: Int) {
        surfaces.remove(index)
    }

    /** What a long press at [window] would pick up, or null when there is no event there. */
    fun candidateAt(window: Offset): DragCandidate? {
        val surface = current() ?: return null
        return surface.timedAt(window, density) ?: surface.allDayAt(window, density)
    }

    /**
     * Picks [candidate] up at [window]. False (and nothing held) when it cannot be picked up: the
     * user is told when it is the calendar that does not allow it.
     */
    fun pickUp(candidate: DragCandidate, window: Offset): Boolean {
        val surface = current()
        val point = surface?.pointAt(window, density)
        val allowed = !busy && point != null && editing.canPickUp(candidate.instance)
        if (!allowed) {
            if (!busy) editing.onBlocked(candidate.instance)
            return false
        }
        val instance = candidate.instance
        grabDay = requireNotNull(surface).page.days.first().toEpochDay() + requireNotNull(point).day
        grabY = point.y
        pointer = window
        val (kind, color) = when (candidate) {
            is DragCandidate.Timed -> candidate.kind to candidate.block.color
            is DragCandidate.AllDay -> DragKind.MOVE to candidate.bar.color
        }
        session = DragSession(instance, kind, instance.time, color)
        haptic(HapticFeedbackType.LongPress)
        return true
    }

    /** The finger moved to [window]. */
    fun move(window: Offset) {
        pointer = window
        refresh()
    }

    /** Works out where the event would go with the finger where it is (also after a scroll). */
    fun refresh() {
        val held = session
        val surface = current()
        val point = surface?.pointAt(pointer, density)
        if (held != null && surface != null && point != null) {
            val days = (surface.page.days.first().toEpochDay() + point.day - grabDay).toInt()
            val bounds = DayBounds.of(surface.page)
            val target = when (val original = held.original) {
                is EventTime.Timed -> {
                    val delta = Delta(days, surface.scale.minutesOf(point.y - grabY))
                    timedTarget(held.kind, original, surface.zone, delta, bounds)
                }

                is EventTime.AllDay -> EventDrag.moveAllDay(original, days, bounds)
            }
            if (target != held.target) {
                session = held.copy(target = target)
                haptic(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    /** The finger let go: the change is saved, unless the event is back where it was. */
    fun release() {
        val held = session ?: return
        session = null
        if (held.target != held.original) editing.onMove(held.instance, held.target)
    }

    /** Puts the event back, saving nothing (back, or a gesture the system took over). */
    fun cancel() {
        session = null
    }

    /** How far the finger went from where it grabbed: whole days and (fractional) minutes. */
    private class Delta(val days: Int, val minutes: Float)

    private fun timedTarget(
        kind: DragKind,
        original: EventTime.Timed,
        zone: ZoneId,
        delta: Delta,
        bounds: DayBounds
    ): EventTime.Timed = when (kind) {
        DragKind.MOVE -> EventDrag.move(original, zone, delta.days, delta.minutes, bounds)
        DragKind.RESIZE -> EventDrag.resize(original, zone, delta.minutes)
    }
}

/** A controller wired to the current screen: density, haptics, and what editing is allowed. */
@Composable
internal fun rememberDragController(editing: GridEditing, busy: Boolean): DragController {
    val controller = remember { DragController() }
    val density = LocalDensity.current.density
    val feedback = LocalHapticFeedback.current
    SideEffect {
        controller.editing = editing
        controller.busy = busy
        controller.density = density
        controller.haptic = { feedback.performHapticFeedback(it) }
    }
    return controller
}
