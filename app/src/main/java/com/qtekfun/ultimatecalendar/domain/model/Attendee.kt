// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

import java.util.Locale

/** An attendee's answer (`ATTENDEE_STATUS_*`, iCalendar `PARTSTAT`). */
enum class AttendeeStatus {
    /** Not answered yet: `NEEDS-ACTION` / `INVITED`. */
    NEEDS_ACTION,
    ACCEPTED,
    TENTATIVE,
    DECLINED;

    val isPending: Boolean get() = this == NEEDS_ACTION
}

enum class AttendeeRole { REQUIRED, OPTIONAL, RESOURCE }

/** A person (or room) invited to an event. [email] is stored trimmed and lowercase. */
data class Attendee(
    val email: String,
    val name: String? = null,
    val role: AttendeeRole = AttendeeRole.REQUIRED,
    val status: AttendeeStatus = AttendeeStatus.NEEDS_ACTION,
    val isOrganizer: Boolean = false
) {
    init {
        require(email == normalize(email)) { "Attendee email must be normalized" }
    }

    /** Whether this attendee is one of the [addresses] (compared without case). */
    fun isOneOf(addresses: Collection<String>): Boolean = addresses.any { normalize(it) == email }

    companion object {
        fun normalize(email: String): String = email.trim().lowercase(Locale.ROOT)

        /** Builds an attendee with a normalized [email]. */
        fun of(
            email: String,
            name: String? = null,
            role: AttendeeRole = AttendeeRole.REQUIRED,
            status: AttendeeStatus = AttendeeStatus.NEEDS_ACTION,
            isOrganizer: Boolean = false
        ) = Attendee(normalize(email), name, role, status, isOrganizer)
    }
}
