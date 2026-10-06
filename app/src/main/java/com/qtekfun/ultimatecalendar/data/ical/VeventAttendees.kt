// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeRole
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import java.util.Locale

/** `ORGANIZER` and `ATTENDEE` (`CN`, `PARTSTAT`, `ROLE`, `CUTYPE`) of a `VEVENT`. */
@Suppress("TooManyFunctions")
internal object VeventAttendees {
    private const val MAILTO = "mailto:"

    /** The address in a calendar-user property: `mailto:`, or the `EMAIL` parameter some servers add. */
    private fun email(property: IcsProperty): String? {
        val value = property.value.trim()
        val address = if (value.startsWith(MAILTO, ignoreCase = true)) {
            value.substring(MAILTO.length)
        } else {
            property.parameter("EMAIL")?.value.orEmpty()
        }
        return Attendee.normalize(address).takeIf { '@' in it }
    }

    fun organizer(vevent: IcsComponent): String? = vevent.property("ORGANIZER")?.let(::email)

    fun read(vevent: IcsComponent, organizer: String?): List<Attendee> =
        vevent.properties.filter { it.name.equals("ATTENDEE", true) }
            .mapNotNull { attendee(it, organizer) }

    private fun attendee(property: IcsProperty, organizer: String?): Attendee? {
        val address = email(property) ?: return null
        return Attendee(
            email = address,
            name = property.parameter("CN")?.value?.trim()?.takeIf { it.isNotEmpty() },
            role = role(property),
            status = status(property.parameter("PARTSTAT")?.value),
            isOrganizer = address == organizer
        )
    }

    private fun role(property: IcsProperty): AttendeeRole {
        val type = property.parameter("CUTYPE")?.value?.uppercase(Locale.ROOT)
        val role = property.parameter("ROLE")?.value?.uppercase(Locale.ROOT)
        return when {
            type == "RESOURCE" || type == "ROOM" -> AttendeeRole.RESOURCE
            role == "OPT-PARTICIPANT" || role == "NON-PARTICIPANT" -> AttendeeRole.OPTIONAL
            else -> AttendeeRole.REQUIRED
        }
    }

    /** DELEGATED, COMPLETED and IN-PROCESS mean no answer of the attendee itself. */
    private fun status(partstat: String?): AttendeeStatus =
        when (partstat?.uppercase(Locale.ROOT)) {
            "ACCEPTED" -> AttendeeStatus.ACCEPTED
            "TENTATIVE" -> AttendeeStatus.TENTATIVE
            "DECLINED" -> AttendeeStatus.DECLINED
            else -> AttendeeStatus.NEEDS_ACTION
        }

    /**
     * [vevent] with [attendees] and [organizer]. Attendee lines that already say the same keep
     * their original text, extra parameters included; unreadable ones (no address) are kept too.
     */
    fun write(vevent: IcsComponent, organizer: String?, attendees: List<Attendee>): IcsComponent {
        val existing = vevent.properties.filter { it.name.equals("ATTENDEE", true) }
            .map { it to attendee(it, organizer) }
        val properties = reconcile(existing, attendees, ::create)
        return vevent.withProperties("ATTENDEE", properties)
    }

    fun writeOrganizer(
        vevent: IcsComponent,
        organizer: String?,
        attendees: List<Attendee>
    ): IcsComponent {
        val property = organizer?.let { address ->
            val name = attendees.firstOrNull { it.email == address }?.name
            IcsProperty(
                "ORGANIZER",
                listOfNotNull(name?.let { IcsParameter("CN", it) }),
                MAILTO + address
            )
        }
        return vevent.withProperty("ORGANIZER", property)
    }

    private fun create(attendee: Attendee): IcsProperty {
        val parameters = buildList {
            attendee.name?.let { add(IcsParameter("CN", it)) }
            if (attendee.role == AttendeeRole.RESOURCE) add(IcsParameter("CUTYPE", "RESOURCE"))
            add(IcsParameter("ROLE", roleText(attendee.role)))
            add(IcsParameter("PARTSTAT", statusText(attendee.status)))
            if (attendee.status == AttendeeStatus.NEEDS_ACTION) add(IcsParameter("RSVP", "TRUE"))
        }
        return IcsProperty("ATTENDEE", parameters, MAILTO + attendee.email)
    }

    private fun roleText(role: AttendeeRole) = when (role) {
        AttendeeRole.REQUIRED -> "REQ-PARTICIPANT"
        AttendeeRole.OPTIONAL -> "OPT-PARTICIPANT"
        AttendeeRole.RESOURCE -> "NON-PARTICIPANT"
    }

    private fun statusText(status: AttendeeStatus) = when (status) {
        AttendeeStatus.NEEDS_ACTION -> "NEEDS-ACTION"
        AttendeeStatus.ACCEPTED -> "ACCEPTED"
        AttendeeStatus.TENTATIVE -> "TENTATIVE"
        AttendeeStatus.DECLINED -> "DECLINED"
    }
}
