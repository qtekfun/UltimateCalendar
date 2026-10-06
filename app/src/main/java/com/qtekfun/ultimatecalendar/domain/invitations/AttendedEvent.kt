// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.security.MessageDigest

/**
 * What is remembered of an upcoming event the user goes to (RF-07, "changes and cancellations"),
 * the minimum to tell on the next check that it moved or was cancelled. For a series it is the
 * next occurrence. The place is kept as a [placeHash], not as text. The [title] is kept only to
 * word the notification of a cancellation, when the event itself is gone from the source.
 * [ownEdit] is set when this app is about to change the event, so that the change is not told.
 */
data class AttendedEvent(
    val key: InvitationKey,
    val title: String,
    val time: EventTime,
    val placeHash: String,
    val ownEdit: Boolean = false
) {
    companion object {
        private const val HASH_CHARS = 16

        /** A digest of [location] without its blanks at both ends; empty for no place. */
        fun placeHash(location: String?): String {
            val text = location.orEmpty().trim()
            if (text.isEmpty()) return ""
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }.take(HASH_CHARS)
        }
    }
}

/** An [AttendedEvent] as read now, with what a notification shows about it. */
data class AttendedSnapshot(val record: AttendedEvent, val shown: Invitation)

/** The result of one run of [AttendedEventDetector.scan]. */
data class AttendedScan(
    /** The events the user goes to that have an upcoming occurrence, soonest first. */
    val tracked: List<AttendedSnapshot>,
    /** Every event read in the run. */
    val seen: Set<InvitationKey>,
    /** The calendars that exist: an event of one that vanished (a removed account) is not cancelled. */
    val calendars: Set<CalendarId>
)

/** What changed in the events the user goes to between two runs. */
data class AttendedChanges(
    val changed: List<InvitationChange>,
    val cancelled: List<Invitation>,
    /** No longer followed (declined, left, past, pending again): their old notes go away. */
    val dropped: List<InvitationKey>
) {
    val isEmpty: Boolean get() = changed.isEmpty() && cancelled.isEmpty() && dropped.isEmpty()
}
