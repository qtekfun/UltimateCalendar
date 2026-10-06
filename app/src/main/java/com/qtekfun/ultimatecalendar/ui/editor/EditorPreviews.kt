// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import androidx.compose.runtime.Composable
import com.qtekfun.ultimatecalendar.domain.editor.EditorDefaults
import com.qtekfun.ultimatecalendar.domain.editor.EventForm
import com.qtekfun.ultimatecalendar.domain.editor.RepeatSetting
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.recurrence.CustomRepeat
import com.qtekfun.ultimatecalendar.domain.recurrence.RepeatEnd
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.ui.components.ComponentPreviews
import com.qtekfun.ultimatecalendar.ui.components.PreviewSurface
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// Invented data only: no real calendar, person or address.
private val PreviewZone: ZoneId = ZoneId.of("Europe/Madrid")
private val PreviewCalendars = listOf(
    CalendarInfo(
        CalendarId(1),
        CalendarAccount("alex@example.org", "bitfire.at.davdroid"),
        "Personal",
        0xFF1A73E8.toInt(),
        CalendarAccess.OWNER,
        ownerEmail = "alex@example.org"
    ),
    CalendarInfo(
        CalendarId(2),
        CalendarAccount("Phone", "LOCAL"),
        "Birthdays",
        0xFFF6BF26.toInt(),
        CalendarAccess.OWNER
    )
)

private fun previewForm(allDay: Boolean = false) = EventForm(
    title = "Planning session",
    allDay = allDay,
    start = LocalDateTime.parse("2026-10-06T10:30").atZone(PreviewZone),
    end = LocalDateTime.parse("2026-10-06T11:30").atZone(PreviewZone),
    zone = PreviewZone,
    location = "Meeting room 3",
    description = "Agenda: roadmap, hiring, budget.",
    calendarId = CalendarId(1),
    reminders = listOf(Reminder(PREVIEW_TEN_MINUTES), Reminder(PREVIEW_ONE_DAY)),
    attendees = listOf(Attendee.of("sam@example.org", name = "Sam")),
    repeat = RepeatSetting.Custom(
        CustomRepeat(
            interval = 2,
            weekdays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            end = RepeatEnd.ON_DATE,
            until = LocalDate.parse("2026-12-18")
        )
    ),
    defaults = EditorDefaults()
)

private fun previewState(form: EventForm, error: CalendarError? = null) = EditorUiState.Ready(
    form = form,
    initial = form,
    calendars = PreviewCalendars,
    saveError = error,
    contactsAvailable = false,
    allDayMinute = PREVIEW_ALL_DAY_MINUTE
)

private const val PREVIEW_ALL_DAY_MINUTE = 540
private const val PREVIEW_TEN_MINUTES = 10
private const val PREVIEW_ONE_DAY = 1440

private val NoActions = EditorActions(
    onEdit = {},
    onSave = {},
    onLeave = {},
    onDismiss = {},
    guests = GuestActions(
        onAdd = { true },
        onType = {},
        onContactsAnswer = {},
        onClearInvalid = {}
    )
)

@ComponentPreviews
@Composable
internal fun EditorTimedPreview() {
    PreviewSurface {
        EventEditorScreen(previewState(previewForm()), PreviewZone, NoActions)
    }
}

@ComponentPreviews
@Composable
internal fun EditorAllDayPreview() {
    PreviewSurface {
        EventEditorScreen(previewState(previewForm(allDay = true)), PreviewZone, NoActions)
    }
}

@ComponentPreviews
@Composable
internal fun EditorProblemPreview() {
    PreviewSurface {
        val broken = previewForm().copy(
            end = LocalDateTime.parse("2026-10-06T09:00").atZone(PreviewZone)
        )
        EventEditorScreen(previewState(broken), PreviewZone, NoActions)
    }
}

@ComponentPreviews
@Composable
internal fun EditorFailedPreview() {
    PreviewSurface {
        EventEditorScreen(
            EditorUiState.Failed(LoadFailure.NO_CALENDAR),
            PreviewZone,
            NoActions
        )
    }
}

@ComponentPreviews
@Composable
internal fun CustomRepeatDialogPreview() {
    PreviewSurface {
        CustomRepeatDialog(
            initial = CustomRepeat(
                interval = 2,
                weekdays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
            ),
            anchor = LocalDate.parse("2026-10-06"),
            onDone = {},
            onDismiss = {}
        )
    }
}
