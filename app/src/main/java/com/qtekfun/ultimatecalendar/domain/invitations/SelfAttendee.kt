// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event

/**
 * The attendee of [event] that is the user: "me" is the owner of the calendar plus the user's
 * aliases ([owners] maps each calendar to its owner's address).
 */
internal fun selfAttendee(
    event: Event,
    owners: Map<CalendarId, String?>,
    aliases: Set<String>
): Attendee? {
    val me = aliases + listOfNotNull(owners[event.calendarId])
    return event.attendees.firstOrNull { it.isOneOf(me) }
}
