// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.settings.AppSettings
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.domain.editor.ContactSuggestions
import com.qtekfun.ultimatecalendar.domain.editor.EditTarget
import com.qtekfun.ultimatecalendar.domain.editor.EditorCalendars
import com.qtekfun.ultimatecalendar.domain.editor.EditorDefaults
import com.qtekfun.ultimatecalendar.domain.editor.EditorRequest
import com.qtekfun.ultimatecalendar.domain.editor.EventForm
import com.qtekfun.ultimatecalendar.domain.editor.EventForms
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.SystemZone
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Builds the first state of the editor for a [EditorRequest]: calendars, defaults, the event. */
class EditorLoader @Inject constructor(
    private val repository: CalendarRepository,
    private val source: CalendarSource,
    private val settings: SettingsRepository,
    private val contacts: ContactSuggestions,
    private val clock: Clock,
    private val zone: SystemZone
) {
    suspend fun load(request: EditorRequest): EditorUiState {
        val all = repository.calendars().first()
        val prefs = settings.current()
        val calendars = EditorCalendars.writable(all.getOrNull().orEmpty())
        return if (all is CalendarResult.Failure) {
            EditorUiState.Failed(LoadFailure.of(all.error))
        } else {
            when (request) {
                is EditorRequest.New -> create(request, calendars, prefs)
                is EditorRequest.Edit -> edit(request, calendars, prefs)
            }
        }
    }

    private suspend fun create(
        request: EditorRequest.New,
        calendars: List<CalendarInfo>,
        prefs: AppSettings
    ): EditorUiState {
        val automatic = repository.defaultCalendar().first().getOrNull()?.id
        val calendar = EditorCalendars.initial(calendars, prefs.defaultCalendar, automatic)
        return if (calendar == null) {
            EditorUiState.Failed(LoadFailure.NO_CALENDAR)
        } else {
            val form = EventForms.create(
                clock,
                zone.current(),
                defaultsOf(prefs),
                calendar,
                request.at
            )
            ready(form, null, calendars, prefs)
        }
    }

    private suspend fun edit(
        request: EditorRequest.Edit,
        calendars: List<CalendarInfo>,
        prefs: AppSettings
    ): EditorUiState = when (val found = source.event(request.ref.eventId)) {
        is CalendarResult.Failure -> EditorUiState.Failed(LoadFailure.of(found.error))

        is CalendarResult.Success -> {
            val event = found.value
            // An access that can edit can also create, so the event's calendar is in the list.
            val calendar = calendars.firstOrNull { it.id == event.calendarId }
            if (calendar?.access?.canEdit != true) {
                EditorUiState.Failed(LoadFailure.READ_ONLY)
            } else {
                val occurrence = request.ref.timeOn(event)
                val form = EventForms.edit(event, occurrence, zone.current(), defaultsOf(prefs))
                ready(form, EditTarget(event, occurrence), calendars, prefs)
            }
        }
    }

    private fun defaultsOf(prefs: AppSettings) = EditorDefaults(
        prefs.defaultDurationMinutes,
        prefs.defaultReminders,
        prefs.defaultAllDayReminders
    )

    private fun ready(
        form: EventForm,
        target: EditTarget?,
        calendars: List<CalendarInfo>,
        prefs: AppSettings
    ) = EditorUiState.Ready(
        form = form,
        initial = form,
        calendars = calendars,
        target = target,
        contactsAvailable = contacts.isAvailable(),
        allDayMinute = prefs.allDayMinute
    )
}
