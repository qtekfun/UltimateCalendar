// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.compose.runtime.Immutable
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.timegrid.AllDayBar
import com.qtekfun.ultimatecalendar.domain.timegrid.TimedBlock

/** What the finger is doing to the event it holds. */
internal enum class DragKind {
    /** The whole event follows the finger (to another time and, in several days, another day). */
    MOVE,

    /** Only the end follows the finger: the duration changes. */
    RESIZE
}

/**
 * An event held by a finger (T18): [instance] as it was, its [original] time and the [target] it
 * would have if let go now. [color] is how it was drawn, to draw it the same on any page.
 */
@Immutable
internal data class DragSession(
    val instance: EventInstance,
    val kind: DragKind,
    val target: EventTime,
    val color: Int?
) {
    val original: EventTime get() = instance.time

    /** The event as it would be if let go now. */
    val ghost: EventInstance get() = instance.copy(time = target)
}

/** What a long press found under the finger. */
internal sealed interface DragCandidate {
    val instance: EventInstance

    data class Timed(val block: TimedBlock, val kind: DragKind) : DragCandidate {
        override val instance: EventInstance get() = block.instance
    }

    data class AllDay(val bar: AllDayBar) : DragCandidate {
        override val instance: EventInstance get() = bar.instance
    }
}

/** What the grid can do to events: pick them up, say why not, and save where they were let go. */
internal data class GridEditing(
    val canPickUp: (EventInstance) -> Boolean = { false },
    val onBlocked: (EventInstance) -> Unit = {},
    val onMove: (EventInstance, EventTime) -> Unit = { _, _ -> }
)

/** The repeating event whose move waits for the user to say which occurrences (T18). */
internal data class ScopeRequest(val move: PendingMove, val scopes: List<RecurrenceScope>)
