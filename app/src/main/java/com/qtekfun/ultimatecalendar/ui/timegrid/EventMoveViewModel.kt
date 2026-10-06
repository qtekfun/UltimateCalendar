// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.EventMover
import com.qtekfun.ultimatecalendar.data.source.MoveUndo
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.timegrid.MoveRule
import com.qtekfun.ultimatecalendar.domain.timegrid.MoveRules
import com.qtekfun.ultimatecalendar.notify.SystemZone
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A change being saved: the grid draws [instance] at [newTime] meanwhile (optimistic). */
data class PendingMove(val instance: EventInstance, val newTime: EventTime)

/** What the user is told about a move, once. */
sealed interface MoveMessage {
    /** The change was saved; [undo] takes it back. */
    data class Moved(val move: PendingMove, val undo: MoveUndo) : MoveMessage

    /** The change failed and the event is where it was. */
    data class Failed(val error: CalendarError) : MoveMessage

    /** Taking the change back failed. */
    data class UndoFailed(val error: CalendarError) : MoveMessage
}

/**
 * Saves what dragging an event on the grid decided (T18, RF-03): shows the new position at once
 * and puts the event back if the source refuses, and lets the change be taken back. What the
 * change does to the source is `EventMover`; this only wires it to the screen.
 */
@HiltViewModel
class EventMoveViewModel @Inject constructor(
    source: CalendarSource,
    calendars: CalendarRepository,
    zone: SystemZone
) : ViewModel() {
    private val mover = EventMover(source, zone::current)
    private val mutablePending = MutableStateFlow<PendingMove?>(null)
    private val messageChannel = Channel<MoveMessage>(Channel.BUFFERED)

    /** The change under way, drawn in place until the new data arrives. */
    val pending: StateFlow<PendingMove?> = mutablePending

    /** Results to tell the user about, each once. */
    val messages: Flow<MoveMessage> = messageChannel.receiveAsFlow()

    /** What may be done to the events of each calendar. */
    val rules: StateFlow<Map<CalendarId, MoveRule>> = calendars.calendars()
        .map { result -> MoveRules.byCalendar(result.getOrNull().orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /** Gives [instance] the time [newTime]; [scope] picks the occurrences of a repeating one. */
    fun move(instance: EventInstance, newTime: EventTime, scope: RecurrenceScope?) {
        if (mutablePending.value != null) return
        val move = PendingMove(instance, newTime)
        mutablePending.value = move
        viewModelScope.launch {
            when (val result = mover.move(instance, newTime, scope)) {
                is CalendarResult.Failure -> {
                    mutablePending.value = null
                    messageChannel.send(MoveMessage.Failed(result.error))
                }

                is CalendarResult.Success -> {
                    messageChannel.send(MoveMessage.Moved(move, result.value))
                    // The new data takes a moment to be read; until then keep the new position.
                    delay(SETTLE_MS)
                    mutablePending.value = null
                }
            }
        }
    }

    /** Takes back a saved move. */
    fun undo(undo: MoveUndo) {
        viewModelScope.launch {
            val result = mover.undo(undo)
            if (result is CalendarResult.Failure) {
                messageChannel.send(MoveMessage.UndoFailed(result.error))
            }
        }
    }

    companion object {
        /** How long the new position is kept after saving, for the grid to read the new data. */
        const val SETTLE_MS = 600L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
