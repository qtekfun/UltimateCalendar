// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.calendar.EventSaver
import com.qtekfun.ultimatecalendar.domain.editor.ContactSuggestions
import com.qtekfun.ultimatecalendar.domain.editor.EditScopes
import com.qtekfun.ultimatecalendar.domain.editor.EditorRequest
import com.qtekfun.ultimatecalendar.domain.editor.EventForm
import com.qtekfun.ultimatecalendar.domain.editor.GuestInput
import com.qtekfun.ultimatecalendar.domain.editor.toDraft
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The create/edit event screen (RF-05). [EditorLoader] builds the first state; this keeps the
 * form in one [state] and saves only when the user says so. Defaults, validation and the mapping
 * to a draft are pure code in `domain.editor`; storing is [EventSaver]. One instance serves
 * every editor session of the activity, so [start] and [reset] bracket each session.
 */
@HiltViewModel
class EventEditorViewModel @Inject constructor(
    private val loader: EditorLoader,
    private val saver: EventSaver,
    private val contacts: ContactSuggestions
) : ViewModel() {
    private val mutable = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val state: StateFlow<EditorUiState> = mutable.asStateFlow()

    private var started: EditorRequest? = null
    private var contactsJob: Job? = null

    /** Opens the editor for [request]; the same request again (a rotation) changes nothing. */
    fun start(request: EditorRequest) {
        if (started == request) return
        started = request
        mutable.value = EditorUiState.Loading
        viewModelScope.launch { mutable.value = loader.load(request) }
    }

    /** Forgets the session, so that the next [start] begins from a blank state. */
    fun reset() {
        started = null
        contactsJob?.cancel()
        mutable.value = EditorUiState.Loading
    }

    /** Applies a change to the form (a field typed, a switch flipped, a picker answered). */
    fun edit(change: (EventForm) -> EventForm) = updateReady { it.copy(form = change(it.form)) }

    /**
     * Adds the addresses typed or picked; true when they were added. Text that is not an address
     * adds nothing and is flagged, for the user to mend.
     */
    fun addGuests(text: String): Boolean {
        val current = state.value as? EditorUiState.Ready ?: return false
        return when (val input = GuestInput.add(current.form, text)) {
            is GuestInput.Added -> {
                mutable.value = current.copy(form = input.form, invalidGuest = null, contacts = emptyList())
                true
            }

            is GuestInput.Invalid -> {
                mutable.value = current.copy(invalidGuest = input.text)
                false
            }
        }
    }

    /**
     * Looks for contacts matching [query], which only returns anything once the permission was
     * granted; call it after the permission answer too, to show the suggestions from then on.
     */
    fun suggestGuests(query: String) {
        contactsJob?.cancel()
        contactsJob = viewModelScope.launch {
            val found = contacts.find(query)
            updateReady { it.copy(contacts = found, contactsAvailable = contacts.isAvailable()) }
        }
    }

    /**
     * Save pressed. For a series it asks "this / this and following / all" first; the answer
     * comes back as [scope].
     */
    fun save(scope: RecurrenceScope? = null) {
        val current = state.value as? EditorUiState.Ready ?: return
        when {
            !current.canSave || (scope != null && scope !in current.scopes) -> Unit

            scope == null && EditScopes.needsChoice(current.target) ->
                mutable.value = current.copy(prompt = EditorPrompt.SCOPE)

            else -> store(current.copy(prompt = null), scope)
        }
    }

    /** Closes whatever question or error is showing. */
    fun dismiss() = updateReady { it.copy(prompt = null, saveError = null, invalidGuest = null) }

    /**
     * Back, up or the system gesture. With unsaved changes it asks first; [discard] answers yes
     * to "discard your changes?" and leaves at once.
     */
    fun leave(discard: Boolean = false) {
        val current = state.value as? EditorUiState.Ready
        when {
            current == null || discard -> mutable.value = EditorUiState.Closed
            current.saving -> Unit
            current.dirty -> mutable.value = current.copy(prompt = EditorPrompt.DISCARD)
            else -> mutable.value = EditorUiState.Closed
        }
    }

    private fun updateReady(transform: (EditorUiState.Ready) -> EditorUiState.Ready) =
        mutable.update { if (it is EditorUiState.Ready) transform(it) else it }

    private fun store(current: EditorUiState.Ready, scope: RecurrenceScope?) {
        val draft = current.form.toDraft() ?: return
        mutable.value = current.copy(saving = true, saveError = null)
        viewModelScope.launch {
            val target = current.target
            val result = if (target == null) {
                saver.create(draft)
            } else {
                saver.update(target, draft, scope)
            }
            when (result) {
                is CalendarResult.Success -> mutable.value = EditorUiState.Closed
                is CalendarResult.Failure ->
                    updateReady { it.copy(saving = false, saveError = result.error) }
            }
        }
    }
}
