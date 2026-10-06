// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Clock

/**
 * Finds the changes and cancellations of the events the user goes to (RF-07, optional): an
 * upcoming event where the user is an attendee who accepted or said maybe, in a calendar where
 * answering is possible (never a read-only one, such as a subscription), with other attendees,
 * and not organised by the user (those changes are the user's own). A pending invitation is not
 * followed here: [InvitationDetector] tells about it.
 *
 * Of a series only the next occurrence is followed. When it has started, the next one is simply
 * taken as the new reference without telling: the series advances. A series split in two shows
 * as the old one ending (no longer upcoming) and a new one (first seen, not told), so it does not
 * repeat itself. "Future" is judged against the injected [clock], in its zone.
 */
class AttendedEventDetector(private val clock: Clock) {

    fun scan(
        events: List<Event>,
        instances: List<EventInstance>,
        calendars: List<CalendarInfo>,
        aliases: Set<String>
    ): AttendedScan {
        val owners = calendars.associate { it.id to it.ownerEmail }
        val accessible = calendars.filter { it.access.canRespond }.map { it.id }.toSet()
        val next = instances.filter { isFuture(it.time) }
            .groupBy { it.eventId }
            .mapValues { (_, list) -> list.map { it.time }.minBy { it.startIn(clock.zone) } }
        val tracked = mutableListOf<AttendedSnapshot>()
        val seen = mutableSetOf<InvitationKey>()
        for (event in events) {
            val key = InvitationKey(event.calendarId, event.id)
            seen += key
            val me = selfAttendee(event, owners, aliases)
            val time = next[event.id] ?: event.time.takeIf { !event.isRecurring && isFuture(it) }
            if (time != null && event.calendarId in accessible && goes(event, me)) {
                val shown = Invitation(key, event.title, time, event.location, event.organizer)
                val record =
                    AttendedEvent(key, event.title, time, AttendedEvent.placeHash(event.location))
                tracked += AttendedSnapshot(record, shown)
            }
        }
        return AttendedScan(
            tracked.sortedBy { it.record.time.startIn(clock.zone) },
            seen,
            calendars.map { it.id }.toSet()
        )
    }

    /** Compares what was followed in the previous run with the current [scan]. */
    fun diff(previous: List<AttendedEvent>, scan: AttendedScan): AttendedChanges {
        val now = scan.tracked.associateBy { it.record.key }
        val changed = mutableListOf<InvitationChange>()
        val cancelled = mutableListOf<Invitation>()
        val dropped = mutableListOf<InvitationKey>()
        for (old in previous) {
            val current = now[old.key]
            when {
                current != null -> if (hasChanged(old, current.record)) {
                    changed += InvitationChange(old.asInvitation(), current.shown)
                }

                // Still there but not followed any more; its calendar is gone; or the user
                // deleted it from this app: nothing to tell.
                old.key in scan.seen || old.key.calendarId !in scan.calendars || old.ownEdit ->
                    dropped += old.key

                else -> cancelled += old.asInvitation()
            }
        }
        return AttendedChanges(changed, cancelled, dropped)
    }

    /** Accepted or maybe, with other attendees, and the user is not the one who organises it. */
    private fun goes(event: Event, me: Attendee?): Boolean =
        me != null && event.attendees.size > 1 && !organisedBy(event, me) &&
            (me.status == AttendeeStatus.ACCEPTED || me.status == AttendeeStatus.TENTATIVE)

    private fun organisedBy(event: Event, me: Attendee): Boolean =
        me.isOrganizer || event.organizer?.let(Attendee::normalize) == me.email

    private fun isFuture(time: EventTime) = time.startIn(clock.zone).isAfter(clock.instant())

    /**
     * Whether the reference moved. One that has already started (the series advanced) or that
     * this app changed is not a change.
     */
    private fun hasChanged(old: AttendedEvent, current: AttendedEvent): Boolean =
        !old.ownEdit && isFuture(old.time) &&
            (old.time != current.time || old.placeHash != current.placeHash)

    private fun AttendedEvent.asInvitation() = Invitation(key, title, time)
}
