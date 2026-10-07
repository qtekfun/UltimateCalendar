// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.domain.invitations.OwnAccounts
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance

/** Which events a check reads one by one to look for invitations (thousands are not read). */
internal object InvitationCandidates {
    /**
     * Whether the user has more than one address (several accounts on the phone, or aliases): the
     * source only says whether the owner of the event's own calendar is an attendee, so an event
     * that invites another of the user's addresses is only found by reading its guest list.
     */
    fun wide(calendars: List<CalendarInfo>, aliases: Set<String>): Boolean =
        aliases.isNotEmpty() || OwnAccounts.addresses(calendars).size > 1

    /**
     * With a single address only an event where the source says the user is an attendee can be an
     * invitation. With [wide], every event that lists attendees is read: one without any cannot
     * be an invitation.
     */
    fun of(instances: List<EventInstance>, wide: Boolean): List<EventId> = instances
        .filter { it.selfStatus != null || (wide && it.hasAttendees) }
        .map { it.eventId }
        .distinct()
}
