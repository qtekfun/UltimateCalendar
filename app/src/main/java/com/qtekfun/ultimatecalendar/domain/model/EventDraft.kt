// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

/** An event not yet stored: the source gives it an id and, for invitations, the organizer. */
data class EventDraft(
    val calendarId: CalendarId,
    val title: String,
    val time: EventTime,
    val location: String? = null,
    val description: String? = null,
    val color: Int? = null,
    val availability: Availability = Availability.BUSY,
    val rrule: String? = null,
    val attendees: List<Attendee> = emptyList(),
    val reminders: List<Reminder> = emptyList()
) {
    /** The stored form of this draft. */
    fun toEvent(id: EventId, organizer: String? = null) = Event(
        id = id,
        calendarId = calendarId,
        title = title,
        time = time,
        location = location,
        description = description,
        color = color,
        availability = availability,
        rrule = rrule,
        organizer = organizer,
        attendees = attendees,
        reminders = reminders
    )
}
