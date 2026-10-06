// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceRules
import java.time.Clock

/**
 * Finds pending invitations the way Apple does: any future event, in any calendar (visible or
 * hidden), where the user is an attendee who has not answered. "Me" is the calendar's owner plus
 * the user's aliases. "Future" is judged against the injected [clock], in its zone.
 */
class InvitationDetector(private val clock: Clock) {

    fun scan(
        events: List<Event>,
        calendars: List<CalendarInfo>,
        aliases: Set<String>
    ): InvitationScan {
        val owners = calendars.associate { it.id to it.ownerEmail }
        val states = LinkedHashMap<InvitationKey, EventState>()
        val pending = mutableListOf<Invitation>()
        for (event in events) {
            val me = attendeeOf(event, owners, aliases)
            val state = EventState(isFuture(event), me?.status)
            val key = InvitationKey(event.calendarId, event.id)
            states[key] = state
            if (state.isFuture && me != null && me.status.isPending) {
                pending += Invitation(key, event.title, event.time, event.location, event.organizer)
            }
        }
        val sorted = pending.sortedBy { it.time.startIn(clock.zone) }
        return InvitationScan(sorted, states)
    }

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

    private fun attendeeOf(
        event: Event,
        owners: Map<CalendarId, String?>,
        aliases: Set<String>
    ): Attendee? {
        val me = aliases + listOfNotNull(owners[event.calendarId])
        return event.attendees.firstOrNull { it.isOneOf(me) }
    }

    private fun isFuture(event: Event): Boolean {
        val now = clock.instant()
        // A series that began in the past still has future occurrences unless it ended.
        return event.time.startIn(clock.zone).isAfter(now) ||
            (event.rrule != null && !hasEnded(event.rrule))
    }

    private fun hasEnded(rrule: String): Boolean {
        val until = RecurrenceRules.parse(rrule)?.until
        return until != null && until.isBefore(clock.instant().atZone(clock.zone).toLocalDate())
    }

    private fun hasMoved(old: Invitation, current: Invitation): Boolean =
        old.time != current.time || place(old) != place(current)

    private fun place(invitation: Invitation): String = invitation.location.orEmpty().trim()
}
