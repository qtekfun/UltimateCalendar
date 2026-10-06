// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import com.qtekfun.ultimatecalendar.domain.detail.EventDetail
import com.qtekfun.ultimatecalendar.domain.detail.EventDetails
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.ui.components.ComponentPreviews
import com.qtekfun.ultimatecalendar.ui.components.PreviewSurface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val Madrid = ZoneId.of("Europe/Madrid")
private val Calendar = CalendarInfo(
    CalendarId(1),
    CalendarAccount("alex@example.org", "com.example"),
    "Work",
    0xFF1A73E8.toInt(),
    CalendarAccess.OWNER,
    ownerEmail = "alex@example.org"
)

private val Invitation = Event(
    id = EventId(1),
    calendarId = CalendarId(1),
    title = "Design review with the whole team",
    time = EventTime.Timed(
        Instant.parse("2026-10-08T07:00:00Z"),
        Instant.parse("2026-10-08T08:00:00Z"),
        ZoneId.of("America/New_York")
    ),
    location = "Meeting room 3, Example Street 1",
    description = "Agenda and notes: https://example.org/notes\n" +
        "Join: https://meet.google.com/abc-defg-hij",
    rrule = "FREQ=WEEKLY;BYDAY=TH",
    organizer = "sam@example.org",
    attendees = listOf(
        Attendee.of(
            "sam@example.org",
            "Sam Rivera",
            isOrganizer = true,
            status = AttendeeStatus.ACCEPTED
        ),
        Attendee.of("alex@example.org", "Alex Doe", status = AttendeeStatus.NEEDS_ACTION),
        Attendee.of("kim@example.org", "Kim Lee", status = AttendeeStatus.ACCEPTED),
        Attendee.of("jo@example.org", status = AttendeeStatus.TENTATIVE),
        Attendee.of("max@example.org", "Max Roe", status = AttendeeStatus.DECLINED)
    ),
    reminders = listOf(Reminder(minutesBefore = 10), Reminder(minutesBefore = 60))
)

private val Holiday = Event(
    id = EventId(2),
    calendarId = CalendarId(1),
    title = "Company holiday",
    time = EventTime.AllDay(LocalDate.parse("2026-12-24"), LocalDate.parse("2026-12-27")),
    reminders = listOf(Reminder(minutesBefore = 900))
)

@Composable
private fun Preview(detail: EventDetail, responding: AttendeeStatus? = null) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    PreviewSurface {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DetailBody(
                detail,
                DetailWords(ResourceWords(resources), locale),
                responding,
                DetailActions()
            )
        }
    }
}

@ComponentPreviews
@Composable
internal fun InvitationDetailPreview() {
    val zone = Madrid
    Preview(
        EventDetails.build(
            Invitation,
            Calendar,
            Invitation.time,
            zone,
            listOf("alex@example.org")
        )
    )
}

@ComponentPreviews
@Composable
internal fun AnsweringDetailPreview() {
    Preview(
        EventDetails.build(Invitation, Calendar, Invitation.time, Madrid, emptyList())
            .answered(AttendeeStatus.TENTATIVE),
        responding = AttendeeStatus.TENTATIVE
    )
}

@ComponentPreviews
@Composable
internal fun ReadOnlyAllDayDetailPreview() {
    Preview(
        EventDetails.build(
            Holiday,
            Calendar.copy(access = CalendarAccess.READ),
            Holiday.time,
            Madrid,
            emptyList()
        )
    )
}
