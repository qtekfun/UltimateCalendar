// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.SeriesChanges
import com.qtekfun.ultimatecalendar.data.source.toDraft
import com.qtekfun.ultimatecalendar.domain.detail.EventDetail
import com.qtekfun.ultimatecalendar.domain.detail.EventDetails
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceSplitter
import com.qtekfun.ultimatecalendar.domain.recurrence.SeriesChange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.SystemZone
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the detail screen is in. */
sealed interface DetailState {
    data object Loading : DetailState

    data class Failed(val error: CalendarError) : DetailState

    /**
     * The event, with [responding] set to the answer being sent (the buttons wait) and
     * [deleting] while a deletion is under way.
     */
    data class Loaded(
        val detail: EventDetail,
        val responding: AttendeeStatus? = null,
        val deleting: Boolean = false
    ) : DetailState {
        val busy: Boolean get() = responding != null || deleting
    }

    /** The event is gone; [canUndo] when it can be put back safely. */
    data class Deleted(val canUndo: Boolean) : DetailState
}

/** What failed, to word the message. */
enum class DetailAction { RESPOND, DELETE, UNDO }

/** One-off news for the screen: a failure to tell the user about. */
data class DetailFailure(val action: DetailAction, val error: CalendarError)

/**
 * Feeds the event detail (RF-04) and carries out its actions: answering an invitation
 * (optimistic: the new answer shows at once and goes back if the source refuses) and deleting
 * an event or part of a series. The decisions are in `domain.detail` and `RecurrenceSplitter`;
 * this only wires them to the [CalendarSource].
 */
@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val source: CalendarSource,
    private val calendars: CalendarRepository,
    private val settings: SettingsRepository,
    private val zone: SystemZone
) : ViewModel() {
    private val mutableState = MutableStateFlow<DetailState>(DetailState.Loading)
    private val failures = Channel<DetailFailure>(Channel.BUFFERED)
    private var ref: EventRef? = null
    private var undo: Undo? = null

    val state: StateFlow<DetailState> = mutableState

    /** Failures of answering, deleting or undoing, each to be shown once. */
    val failure: Flow<DetailFailure> = failures.receiveAsFlow()

    init {
        viewModelScope.launch {
            source.changes.collect {
                if ((mutableState.value as? DetailState.Loaded)?.busy == false) reload()
            }
        }
    }

    /** Shows [target]; opening the same occurrence again keeps what is already shown. */
    fun open(target: EventRef) {
        if (target == ref) return
        ref = target
        undo = null
        mutableState.value = DetailState.Loading
        viewModelScope.launch { reload() }
    }

    /** Tries again after a failed read. */
    fun retry() {
        mutableState.value = DetailState.Loading
        viewModelScope.launch { reload() }
    }

    /** Answers the invitation as the user. Does nothing when the user cannot or is answering. */
    fun respond(status: AttendeeStatus) {
        val loaded = mutableState.value as? DetailState.Loaded ?: return
        val previous = loaded.detail
        if (!previous.canRespond || loaded.busy || previous.self?.status == status) return
        mutableState.value = DetailState.Loaded(previous.answered(status), responding = status)
        viewModelScope.launch {
            val result = source.respond(previous.event.id, status)
            if (result is CalendarResult.Failure) {
                mutableState.value = DetailState.Loaded(previous)
                failures.send(DetailFailure(DetailAction.RESPOND, result.error))
            } else {
                mutableState.update { (it as? DetailState.Loaded)?.copy(responding = null) ?: it }
                reload()
            }
        }
    }

    /**
     * Deletes the event; for a repeating one, only the occurrence, this and the following ones
     * or all of them, as [scope] says (it is ignored for a single event).
     */
    fun delete(scope: RecurrenceScope) {
        val loaded = mutableState.value as? DetailState.Loaded ?: return
        val detail = loaded.detail
        if (!detail.canEdit || loaded.busy) return
        mutableState.value = loaded.copy(deleting = true)
        viewModelScope.launch {
            val change = changeFor(detail, scope)
            val result = when (change) {
                is CalendarResult.Failure -> change
                is CalendarResult.Success -> SeriesChanges.apply(source, detail.event, change.value)
            }
            if (result is CalendarResult.Failure) {
                mutableState.value = loaded
                failures.send(DetailFailure(DetailAction.DELETE, result.error))
            } else {
                undo = change.getOrNull()?.let { undoOf(detail.event, it) }
                mutableState.value = DetailState.Deleted(undo != null)
            }
        }
    }

    /** Puts back what [delete] removed, when it said it could. */
    fun undoDelete() {
        val plan = undo ?: return
        undo = null
        mutableState.value = DetailState.Loading
        viewModelScope.launch {
            val restored = when (plan) {
                is Undo.Recreate -> source.create(plan.event.toDraft()).map { id ->
                    ref = ref?.copy(eventId = id)
                }

                is Undo.Restore -> source.update(plan.event)
            }
            if (restored is CalendarResult.Failure) {
                failures.send(DetailFailure(DetailAction.UNDO, restored.error))
                mutableState.value = DetailState.Deleted(canUndo = false)
            } else {
                reload()
            }
        }
    }

    private fun changeFor(
        detail: EventDetail,
        scope: RecurrenceScope
    ): CalendarResult<SeriesChange> = if (detail.isSeries) {
        RecurrenceSplitter.delete(detail.event, detail.occurrence, scope)
    } else {
        CalendarResult.Success(SeriesChange.Delete(detail.event.id))
    }

    /**
     * What puts the deleted back: only when nobody else is invited (deleting would have told
     * them, and creating again would invite them again) and when the source can store it.
     */
    private fun undoOf(event: Event, change: SeriesChange): Undo? = when {
        event.attendees.isNotEmpty() -> null
        change is SeriesChange.Delete -> Undo.Recreate(event)
        change is SeriesChange.Split -> Undo.Restore(event)
        else -> null
    }

    private suspend fun reload() {
        val target = ref ?: return
        when (val found = source.event(target.eventId)) {
            is CalendarResult.Failure -> mutableState.update {
                val gone = found.error is CalendarError.NotFound
                if (it is DetailState.Loaded && !gone) it else DetailState.Failed(found.error)
            }

            is CalendarResult.Success -> {
                val event = found.value
                val all = calendars.calendars().first().getOrNull().orEmpty()
                val detail = EventDetails.build(
                    event,
                    all.firstOrNull { it.id == event.calendarId },
                    target.timeOn(event),
                    zone.current(),
                    settings.current().ownEmails
                )
                mutableState.update { current ->
                    val busy = current as? DetailState.Loaded
                    DetailState.Loaded(detail, busy?.responding, busy?.deleting == true)
                }
            }
        }
    }

    private sealed interface Undo {
        data class Recreate(val event: Event) : Undo

        data class Restore(val event: Event) : Undo
    }
}
