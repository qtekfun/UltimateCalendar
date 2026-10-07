// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime

/**
 * Identifies an invitation across runs: an event and the address of the user's own it invites.
 * [address] is blank for the account that owns the calendar the event lives in (and for the
 * aliases of Settings), which is how every invitation was keyed before the user's other accounts
 * counted; it is the normalized address of another of the user's accounts when the event lives in
 * a calendar of a different account, so an event that invites two of them is two invitations.
 */
data class InvitationKey(
    val calendarId: CalendarId,
    val eventId: EventId,
    val address: String = ""
) {
    /** Whether the invitation is for another of the user's accounts than the event's own. */
    val isForeign: Boolean get() = address.isNotEmpty()
}

/**
 * A future event in which the user has not answered yet. [account] is the address of the user
 * that the invitation is for, set only when the user has more than one, so that the screens can
 * say which account it is for.
 */
data class Invitation(
    val key: InvitationKey,
    val title: String,
    val time: EventTime,
    val location: String? = null,
    val organizer: String? = null,
    val account: String? = null
)
