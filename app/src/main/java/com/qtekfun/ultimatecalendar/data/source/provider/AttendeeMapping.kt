// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Attendees
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeRole
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus

/** `Attendees` rows to [Attendee] and back. */
internal object AttendeeMapping {
    val projection = listOf(
        Attendees._ID,
        Attendees.ATTENDEE_NAME,
        Attendees.ATTENDEE_EMAIL,
        Attendees.ATTENDEE_RELATIONSHIP,
        Attendees.ATTENDEE_TYPE,
        Attendees.ATTENDEE_STATUS
    )

    /** `ATTENDEE_STATUS_*`; "none" and "invited" both mean no answer yet. */
    fun statusOf(code: Int?): AttendeeStatus = when (code) {
        Attendees.ATTENDEE_STATUS_ACCEPTED -> AttendeeStatus.ACCEPTED
        Attendees.ATTENDEE_STATUS_DECLINED -> AttendeeStatus.DECLINED
        Attendees.ATTENDEE_STATUS_TENTATIVE -> AttendeeStatus.TENTATIVE
        else -> AttendeeStatus.NEEDS_ACTION
    }

    fun statusCode(status: AttendeeStatus): Int = when (status) {
        AttendeeStatus.NEEDS_ACTION -> Attendees.ATTENDEE_STATUS_INVITED
        AttendeeStatus.ACCEPTED -> Attendees.ATTENDEE_STATUS_ACCEPTED
        AttendeeStatus.TENTATIVE -> Attendees.ATTENDEE_STATUS_TENTATIVE
        AttendeeStatus.DECLINED -> Attendees.ATTENDEE_STATUS_DECLINED
    }

    /** `ATTENDEE_TYPE_*`; "none" is a required attendee. */
    fun roleOf(code: Int?): AttendeeRole = when (code) {
        Attendees.TYPE_OPTIONAL -> AttendeeRole.OPTIONAL
        Attendees.TYPE_RESOURCE -> AttendeeRole.RESOURCE
        else -> AttendeeRole.REQUIRED
    }

    fun roleCode(role: AttendeeRole): Int = when (role) {
        AttendeeRole.REQUIRED -> Attendees.TYPE_REQUIRED
        AttendeeRole.OPTIONAL -> Attendees.TYPE_OPTIONAL
        AttendeeRole.RESOURCE -> Attendees.TYPE_RESOURCE
    }

    /** The attendee of [row], or null when it has no address (nothing to identify it by). */
    fun toAttendee(row: ProviderRow): Attendee? {
        val email = row.text(Attendees.ATTENDEE_EMAIL)?.takeIf { it.isNotBlank() }
        return email?.let {
            Attendee.of(
                email = it,
                name = row.text(Attendees.ATTENDEE_NAME)?.takeIf { name -> name.isNotBlank() },
                role = roleOf(row.int(Attendees.ATTENDEE_TYPE)),
                status = statusOf(row.int(Attendees.ATTENDEE_STATUS)),
                isOrganizer = row.int(Attendees.ATTENDEE_RELATIONSHIP) ==
                    Attendees.RELATIONSHIP_ORGANIZER
            )
        }
    }

    /** The row to insert for [attendee]; [eventId] is null when a batch back reference sets it. */
    fun toValues(attendee: Attendee, eventId: Long?): ProviderRow = buildMap {
        eventId?.let { put(Attendees.EVENT_ID, it) }
        put(Attendees.ATTENDEE_EMAIL, attendee.email)
        put(Attendees.ATTENDEE_NAME, attendee.name)
        put(
            Attendees.ATTENDEE_RELATIONSHIP,
            if (attendee.isOrganizer) {
                Attendees.RELATIONSHIP_ORGANIZER
            } else {
                Attendees.RELATIONSHIP_ATTENDEE
            }
        )
        put(Attendees.ATTENDEE_TYPE, roleCode(attendee.role))
        put(Attendees.ATTENDEE_STATUS, statusCode(attendee.status))
    }
}
