// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

/** Whether the event blocks the time (`Events.AVAILABILITY`). */
enum class Availability { BUSY, FREE, TENTATIVE }

/**
 * An event as stored in its source. [rrule] is the raw RRULE text, kept as it is so that rules
 * the app does not understand survive an edit; read it with `RecurrenceRules.parse`.
 * [color] is ARGB, or null to use the calendar's.
 */
data class Event(
    val id: EventId,
    val calendarId: CalendarId,
    val title: String,
    val time: EventTime,
    val location: String? = null,
    val description: String? = null,
    val color: Int? = null,
    val availability: Availability = Availability.BUSY,
    val rrule: String? = null,
    val organizer: String? = null,
    val attendees: List<Attendee> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    /**
     * The iCalendar UID (`UID_2445`): the same in every copy of an invitation that lands in the
     * calendars of the guests' accounts, so copies of one event can be told. Null when unknown.
     */
    val uid: String? = null
) {
    val isRecurring: Boolean get() = rrule != null

    val isAllDay: Boolean get() = time is EventTime.AllDay
}
