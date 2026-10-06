// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.detail

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.Event

/** One line of the attendee list; [isSelf] is the user, who is highlighted. */
data class AttendeeRow(val attendee: Attendee, val isSelf: Boolean) {
    /** What to show first: the name, or the address when there is none. */
    val label: String get() = attendee.name?.takeIf { it.isNotBlank() } ?: attendee.email
}

/** The attendees that gave the same answer. */
data class AttendeeGroup(val status: AttendeeStatus, val rows: List<AttendeeRow>)

/** The attendees of an event: the organizer first, then one group per answer. */
data class AttendeeGroups(val organizer: AttendeeRow?, val groups: List<AttendeeGroup>) {
    /** Everybody, organizer included. */
    val total: Int get() = groups.sumOf { it.rows.size } + if (organizer == null) 0 else 1
}

object AttendeeGrouping {
    /** Accepted, maybe, declined, then no answer, as Google Calendar lists them. */
    private val ORDER = listOf(
        AttendeeStatus.ACCEPTED,
        AttendeeStatus.TENTATIVE,
        AttendeeStatus.DECLINED,
        AttendeeStatus.NEEDS_ACTION
    )

    /**
     * Groups the attendees of [event]; null when it has none. [me] are the addresses of the
     * user. The organizer is the attendee flagged as such or, failing that, the one with the
     * event's organizer address; an organizer that is not listed is added as accepted. Inside a
     * group the user comes first, then people by name.
     */
    fun group(event: Event, me: Collection<String>): AttendeeGroups? {
        if (event.attendees.isEmpty()) return null
        val organizerAddress = event.organizer?.let(Attendee::normalize)
        val organizer = event.attendees.firstOrNull { it.isOrganizer }
            ?: event.attendees.firstOrNull { it.email == organizerAddress }
            ?: organizerAddress?.let {
                Attendee(it, status = AttendeeStatus.ACCEPTED, isOrganizer = true)
            }
        val others = event.attendees.filter { it.email != organizer?.email }
        val groups = ORDER.mapNotNull { status ->
            val rows = others.filter { it.status == status }
                .map { AttendeeRow(it, it.isOneOf(me)) }
                .sortedWith(compareBy({ !it.isSelf }, { it.label.lowercase() }))
            if (rows.isEmpty()) null else AttendeeGroup(status, rows)
        }
        return AttendeeGroups(organizer?.let { AttendeeRow(it, it.isOneOf(me)) }, groups)
    }
}
