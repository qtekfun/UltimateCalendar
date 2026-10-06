// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

/**
 * One occurrence of an event in a range, as the source's `Instances` returns it. The views draw
 * these; repetitions are never expanded by the app (CLAUDE.md). [selfStatus] is the user's own
 * answer, or null when the user is not an attendee.
 */
data class EventInstance(
    val eventId: EventId,
    val calendarId: CalendarId,
    val title: String,
    val time: EventTime,
    val location: String? = null,
    val color: Int? = null,
    val isRecurring: Boolean = false,
    val selfStatus: AttendeeStatus? = null
)
