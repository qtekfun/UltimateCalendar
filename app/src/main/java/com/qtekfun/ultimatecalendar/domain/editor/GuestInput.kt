// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules

/** The outcome of typing addresses into the guests field. */
sealed interface GuestInput {
    /** [form] has the new guests; [skipped] were already there (or are the organizer). */
    data class Added(val form: EventForm, val skipped: Int) : GuestInput

    /** [text] holds something that is not an address, so nothing was added. */
    data class Invalid(val text: String) : GuestInput

    companion object {
        /**
         * Adds the addresses in [text], separated by commas, semicolons, spaces or new lines, as
         * a pasted list or the end of a typed one. Either every piece is an address or nothing
         * is added. An address already invited, or the organizer's own, is skipped.
         */
        fun add(form: EventForm, text: String): GuestInput {
            val pieces = text.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }
            if (pieces.isEmpty()) return Invalid(text)
            val addresses = pieces.map { SettingsRules.alias(it) ?: return Invalid(it) }.distinct()
            val taken = form.attendees.map { it.email } + listOfNotNull(form.organizer)
                .map(Attendee::normalize)
            val fresh = addresses.filterNot { it in taken }
            return Added(
                form.copy(attendees = form.attendees + fresh.map { Attendee.of(it) }),
                skipped = addresses.size - fresh.size
            )
        }

        private val SEPARATORS = Regex("[,;\\s]+")
    }
}

/** Removes [guest] from the invited people; the organizer cannot be removed. */
fun EventForm.withoutGuest(guest: Attendee): EventForm =
    if (guest.isOrganizer) this else copy(attendees = attendees - guest)
