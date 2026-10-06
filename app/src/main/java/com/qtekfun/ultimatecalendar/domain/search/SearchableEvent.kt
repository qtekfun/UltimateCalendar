// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime

/**
 * An event as search sees it: the text fields it looks in and the series' own time (the first
 * occurrence). A repeating series is one [SearchableEvent]; which occurrence to show is chosen
 * later by [OccurrencePicker].
 */
data class SearchableEvent(
    val eventId: EventId,
    val calendarId: CalendarId,
    val title: String,
    val time: EventTime,
    val location: String? = null,
    val description: String? = null,
    val color: Int? = null,
    val isRecurring: Boolean = false,
    val attendees: List<Attendee> = emptyList()
) {
    /** The series' own first occurrence, used when the source reports no occurrence of it. */
    fun asInstance() = EventInstance(
        eventId = eventId,
        calendarId = calendarId,
        title = title,
        time = time,
        location = location,
        color = color,
        isRecurring = isRecurring
    )

    companion object {
        fun of(event: Event) = SearchableEvent(
            eventId = event.id,
            calendarId = event.calendarId,
            title = event.title,
            time = event.time,
            location = event.location,
            description = event.description,
            color = event.color,
            isRecurring = event.isRecurring,
            attendees = event.attendees
        )
    }
}
