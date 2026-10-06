// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import java.time.Instant

/** `STATUS` of a `VEVENT` (RFC 5545 §3.8.1.11). */
enum class EventStatus { CONFIRMED, TENTATIVE, CANCELLED }

/**
 * What the app reads from and writes to a `VEVENT`. Everything else (ATTACH, CATEGORIES, X-
 * properties, alarms the app cannot express…) is kept as it is. [recurrenceId] is set for an
 * override of one occurrence of a series; [exDates] and [rDates] belong to the master.
 * [sequence] is read-only: writing bumps it by itself when anything changed.
 */
data class VeventFields(
    val uid: String,
    val title: String,
    val time: EventTime,
    val location: String? = null,
    val description: String? = null,
    val availability: Availability = Availability.BUSY,
    val status: EventStatus = EventStatus.CONFIRMED,
    val rrule: String? = null,
    val exDates: Set<OccurrenceKey> = emptySet(),
    val rDates: Set<OccurrenceKey> = emptySet(),
    val recurrenceId: OccurrenceKey? = null,
    val sequence: Int = 0,
    /** The organizer's address, normalized (see [Attendee.normalize]). */
    val organizer: String? = null,
    val attendees: List<Attendee> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val modifiedAt: Instant? = null
) {
    /** The domain event. */
    fun toEvent(id: EventId, calendarId: CalendarId) = Event(
        id = id,
        calendarId = calendarId,
        title = title,
        time = time,
        location = location,
        description = description,
        availability = availability,
        rrule = rrule,
        organizer = organizer,
        attendees = attendees,
        reminders = reminders
    )

    companion object {
        /** The fields of [event]; the organizer is also taken from its attendee flagged as such. */
        fun of(
            event: Event,
            uid: String,
            status: EventStatus = EventStatus.CONFIRMED,
            exDates: Set<OccurrenceKey> = emptySet(),
            rDates: Set<OccurrenceKey> = emptySet(),
            recurrenceId: OccurrenceKey? = null
        ) = VeventFields(
            uid = uid,
            title = event.title,
            time = event.time,
            location = event.location,
            description = event.description,
            availability = event.availability,
            status = status,
            rrule = event.rrule,
            exDates = exDates,
            rDates = rDates,
            recurrenceId = recurrenceId,
            organizer = event.organizer ?: event.attendees.firstOrNull { it.isOrganizer }?.email,
            attendees = event.attendees,
            reminders = event.reminders
        )
    }
}
