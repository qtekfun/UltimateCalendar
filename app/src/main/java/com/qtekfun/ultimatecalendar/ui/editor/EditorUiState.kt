// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import com.qtekfun.ultimatecalendar.domain.editor.ContactSuggestion
import com.qtekfun.ultimatecalendar.domain.editor.EditScopes
import com.qtekfun.ultimatecalendar.domain.editor.EditTarget
import com.qtekfun.ultimatecalendar.domain.editor.EventColorSupport
import com.qtekfun.ultimatecalendar.domain.editor.EventForm
import com.qtekfun.ultimatecalendar.domain.editor.FormIssue
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.result.CalendarError

/** A question the editor is waiting for the user to answer. */
enum class EditorPrompt {
    /** "Only this event, this and following, or all events?" before saving a series. */
    SCOPE,

    /** "Discard your changes?" when leaving with edits. */
    DISCARD
}

/** Why the editor cannot open. */
enum class LoadFailure {
    /** No calendar accepts events. */
    NO_CALENDAR,

    /** The event is gone. */
    NOT_FOUND,

    /** The event's calendar does not let the user change it. */
    READ_ONLY,

    PERMISSION_DENIED,

    /** The source failed or rejected the read. */
    SOURCE_FAILURE;

    companion object {
        fun of(error: CalendarError): LoadFailure = when (error) {
            CalendarError.PermissionDenied -> PERMISSION_DENIED
            CalendarError.NotFound -> NOT_FOUND
            CalendarError.ReadOnly -> READ_ONLY
            is CalendarError.Invalid, is CalendarError.SourceFailure -> SOURCE_FAILURE
        }
    }
}

/** The whole state of the editor screen (RF-05). */
sealed interface EditorUiState {
    data object Loading : EditorUiState

    data class Failed(val reason: LoadFailure) : EditorUiState

    /** Saved or discarded: the screen goes back. */
    data object Closed : EditorUiState

    data class Ready(
        val form: EventForm,
        /** The form as it was opened, to know whether anything changed. */
        val initial: EventForm,
        /** Calendars that accept events. */
        val calendars: List<CalendarInfo>,
        /** The series being edited; null for a new event. */
        val target: EditTarget? = null,
        val prompt: EditorPrompt? = null,
        val saving: Boolean = false,
        /** Why the last save failed; the form is kept as it was. */
        val saveError: CalendarError? = null,
        /** The text of the guests field that was not an address. */
        val invalidGuest: String? = null,
        val contacts: List<ContactSuggestion> = emptyList(),
        val contactsAvailable: Boolean = false,
        /** When all-day events remind, as minutes after midnight (Settings). */
        val allDayMinute: Int = 0
    ) : EditorUiState {
        val isNew: Boolean get() = target == null

        val dirty: Boolean get() = form != initial

        val issues: Set<FormIssue> get() = form.issues

        val calendar: CalendarInfo? get() = calendars.firstOrNull { it.id == form.calendarId }

        val supportsColor: Boolean get() = EventColorSupport.supports(calendar)

        /** What to ask before saving a series; "only this event" is missing in local calendars. */
        val scopes: List<RecurrenceScope> get() = EditScopes.available(calendar)

        val canSave: Boolean get() = form.isValid && !saving && (isNew || dirty)

        /** The organizer shown read-only: the event's, or the calendar owner for a new one. */
        val organizer: String? get() = form.organizer ?: calendar?.ownerEmail
    }
}
