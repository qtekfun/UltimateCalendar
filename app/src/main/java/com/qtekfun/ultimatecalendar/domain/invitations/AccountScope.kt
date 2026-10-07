// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event

/**
 * Who "me" is in one scan: the addresses of every account of the device plus the user's aliases
 * ([own]), and which of them are the user in the calendar an event lives in.
 */
internal class AccountScope(calendars: List<CalendarInfo>, aliases: Set<String>) {
    private val aliases: Set<String> = aliases.mapTo(linkedSetOf(), Attendee::normalize)
    private val byCalendar: Map<CalendarId, Set<String>> =
        calendars.associate { it.id to accountsOf(it) }

    /** Every address that is the user somewhere on the phone. */
    val own: Set<String> = OwnAccounts.addresses(calendars) + this.aliases

    /** Whether the user has more than one address, so invitations say which one they are for. */
    val hasSeveral: Boolean get() = own.size > 1

    /** The addresses that are the user in [calendar]: its accounts and the aliases. */
    fun localAddresses(calendar: CalendarId): Set<String> = byCalendar[calendar].orEmpty() + aliases

    /** Whether [event] is organised by the user's account that owns its calendar. */
    fun isOrganised(event: Event): Boolean =
        event.organizer?.let(Attendee::normalize) in localAddresses(event.calendarId)

    private fun accountsOf(calendar: CalendarInfo): Set<String> =
        OwnAccounts.addressesOf(calendar) +
            listOfNotNull(calendar.ownerEmail?.let(Attendee::normalize))
}
