// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import java.time.Clock

/**
 * Finds pending invitations the way Apple does: any future event, in any calendar (visible or
 * hidden), where the user is an attendee who has not answered. "Me" is every account of the
 * device ([OwnAccounts]) plus the user's aliases, wherever the event lives: an event of account A
 * that invites the user's account B is an invitation for B, found even when B's own calendar has
 * no copy yet. When B's calendar has its copy, that copy is the invitation (it is where the answer
 * must be written) and A's copy adds none. An event that invites two of the user's accounts is two
 * invitations. "Future" is judged against the injected [clock], in its zone.
 */
class InvitationDetector(private val clock: Clock) {
    private val upcoming = Upcoming(clock)
    private val foreign = ForeignInvitations(clock, upcoming)

    fun scan(
        events: List<Event>,
        calendars: List<CalendarInfo>,
        aliases: Set<String>
    ): InvitationScan {
        val scope = AccountScope(calendars, aliases)
        val out = ScanResult()
        val others = events.flatMap { note(it, scope, out) }
        foreign.settle(others, events, scope, out)
        return InvitationScan(out.pending.sortedBy { it.time.startIn(clock.zone) }, out.states)
    }

    /** Records [event] for the account of its calendar; returns the other accounts it invites. */
    private fun note(event: Event, scope: AccountScope, out: ScanResult): List<Foreign> {
        val me = event.attendees.firstOrNull { it.isOneOf(scope.localAddresses(event.calendarId)) }
        val future = upcoming.isFuture(event)
        val key = InvitationKey(event.calendarId, event.id)
        out.states[key] = EventState(future, me?.status)
        val invited = me?.takeIf(::isInvited)
        if (future && invited != null) {
            out.pending += Invitation(
                key,
                event.title,
                event.time,
                event.location,
                event.organizer,
                account = invited.email.takeIf { scope.hasSeveral }
            )
        }
        return foreign.guestsOf(event, scope)
    }

    /** An attendee who has not answered; the user's own organizer row is never an invitation. */
    private fun isInvited(me: Attendee): Boolean = me.status.isPending && !me.isOrganizer

    /** Compares the invitations notified in the previous run with the current [scan]. */
    fun diff(previous: List<Invitation>, scan: InvitationScan): InvitationChanges {
        val before = previous.associateBy { it.key }
        val now = scan.pending.associateBy { it.key }
        val changed = mutableListOf<InvitationChange>()
        val cancelled = mutableListOf<Invitation>()
        val answered = mutableListOf<Invitation>()
        for (old in before.values) {
            val current = now[old.key]
            if (current != null) {
                if (hasMoved(old, current)) changed += InvitationChange(old, current)
                continue
            }
            val state = scan.states[old.key]
            when {
                state == null -> cancelled += old
                !state.isFuture -> Unit
                state.myStatus == null -> cancelled += old
                else -> answered += old
            }
        }
        return InvitationChanges(
            new = scan.pending.filter { it.key !in before },
            changed = changed,
            cancelled = cancelled,
            answeredElsewhere = answered
        )
    }

    private fun hasMoved(old: Invitation, current: Invitation): Boolean =
        old.time != current.time || place(old) != place(current)

    private fun place(invitation: Invitation): String = invitation.location.orEmpty().trim()
}
