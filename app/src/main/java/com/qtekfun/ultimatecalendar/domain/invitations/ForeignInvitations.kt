// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.Event
import java.time.Clock

/** An own address that an event of another account's calendar invites. */
internal class Foreign(val event: Event, val guest: Attendee) {
    val key = InvitationKey(event.calendarId, event.id, guest.email)
}

/** What a scan collects: the state of every event and the invitations found. */
internal class ScanResult {
    val states = LinkedHashMap<InvitationKey, EventState>()
    val pending = mutableListOf<Invitation>()
}

/**
 * The invitations for the user's other accounts (A organises and invites B, both are the user's).
 * The same invitation can show up in several events: the organizer's, one per account that was
 * invited too, and B's own once it syncs. B's own copy decides when it exists (it is where the
 * answer must be written); otherwise one copy is chosen, the organizer's first.
 */
internal class ForeignInvitations(private val clock: Clock, private val upcoming: Upcoming) {
    /** The own addresses invited to [event] in a calendar that is not their account's. */
    fun guestsOf(event: Event, scope: AccountScope): List<Foreign> {
        val local = scope.localAddresses(event.calendarId)
        return event.attendees
            .filter { it.email in scope.own && !it.isOrganizer && !it.isOneOf(local) }
            .map { Foreign(event, it) }
    }

    /** Settles every [foreign] invitation of [events] into [out]. */
    fun settle(foreign: List<Foreign>, events: List<Event>, scope: AccountScope, out: ScanResult) {
        val byIdentity = events.groupBy { identity(it) }
        for ((_, group) in foreign.groupBy { identity(it.event) to it.guest.email }) {
            val address = group.first().guest.email
            val copy = byIdentity.getValue(identity(group.first().event))
                .firstOrNull { address in scope.localAddresses(it.calendarId) }
            // With the invited account's own copy, that copy decides; else the best copy does.
            val chosen = if (copy == null) group.minWith(preference(scope)) else null
            for (candidate in group) {
                val status = if (copy == null) candidate.guest.status else answerIn(copy, address)
                val future = upcoming.isFuture(candidate.event)
                out.states[candidate.key] = EventState(future, status)
                if (candidate === chosen && status.isPending && future) {
                    out.pending += Invitation(
                        candidate.key,
                        candidate.event.title,
                        candidate.event.time,
                        candidate.event.location,
                        candidate.event.organizer,
                        account = address
                    )
                }
            }
        }
    }

    /** Which copy of an invitation to keep: the organizer's, else the oldest calendar's. */
    private fun preference(scope: AccountScope): Comparator<Foreign> = compareBy<Foreign>(
        { !scope.isOrganised(it.event) },
        { it.event.calendarId.value },
        { it.event.id.value }
    )

    /** The answer of [address] in its own [copy] of an event (accepted when it is not listed). */
    private fun answerIn(copy: Event, address: String): AttendeeStatus {
        val listed = copy.attendees.firstOrNull { it.email == address }
        return if (listed == null) AttendeeStatus.ACCEPTED else listed.status
    }

    private fun identity(event: Event): String = EventCopies.identity(event, clock.zone)
}
