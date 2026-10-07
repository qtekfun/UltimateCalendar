// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo

/**
 * Who "me" is: every address of the accounts of the device, as the calendars tell (RF-06). Many
 * people have several Google accounts on the phone and invite one from another, so an invitation
 * is for the user wherever it lives, not only in the calendar of the account it is addressed to.
 */
object OwnAccounts {
    private const val GROUP = "@group.calendar.google.com"
    private const val GROUP_V = "@group.v.calendar.google.com"
    private const val RESOURCE = "@resource.calendar.google.com"

    /**
     * The addresses of the accounts of [calendars], normalized: the account name when it is an
     * address, and the owner of a calendar the user owns. Group, holiday and read-only calendars
     * belong to somebody else or to nobody and add nothing; on-device and subscription accounts
     * have no address.
     */
    fun addresses(calendars: List<CalendarInfo>): Set<String> =
        calendars.flatMapTo(linkedSetOf()) { addressesOf(it) }

    /** Every address that names the user in [calendar]: its account and its owner. */
    fun addressesOf(calendar: CalendarInfo): Set<String> {
        val account = calendar.account
        val named = account.name.takeIf { '@' in it && !account.isSubscription && !account.isLocal }
            ?.let(Attendee::normalize)
        // A calendar shared with the user names its owner, who is somebody else: the owner is
        // trusted only for a calendar the user owns and that agrees with its account.
        val owner = calendar.ownerEmail?.let(Attendee::normalize)
        val trusted = owner != null && calendar.access == CalendarAccess.OWNER &&
            (named == null || owner == named)
        return listOfNotNull(named, owner.takeIf { trusted })
            .filterNotTo(linkedSetOf()) { isShared(it) }
    }

    /** The calendars whose owner or account is [address]. */
    fun calendarsOf(calendars: List<CalendarInfo>, address: String): List<CalendarInfo> =
        calendars.filter { address in addressesOf(it) }

    /** A calendar address that is not a person: Google's group, holiday and resource calendars. */
    fun isShared(address: String): Boolean = address.startsWith("#") ||
        address.endsWith(GROUP) || address.endsWith(GROUP_V) || address.endsWith(RESOURCE)
}
