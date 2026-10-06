// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

/** An invitation already notified whose date, time or place changed afterwards. */
data class InvitationChange(val previous: Invitation, val current: Invitation)

/** The differences between two runs of the detector. */
data class InvitationChanges(
    /** Pending now, not before. */
    val new: List<Invitation>,
    val changed: List<InvitationChange>,
    /** Deleted by the organizer, or the user was removed from it. */
    val cancelled: List<Invitation>,
    /** Answered from another app or device. */
    val answeredElsewhere: List<Invitation>,
    /** An event the user goes to (accepted or maybe) whose date, time, zone or place changed. */
    val attendedChanged: List<InvitationChange> = emptyList(),
    /** An event the user goes to that the organizer cancelled or deleted. */
    val attendedCancelled: List<Invitation> = emptyList(),
    /** Events the user no longer goes to: their notes on the changes channel go away. */
    val attendedDropped: List<InvitationKey> = emptyList()
) {
    val isEmpty: Boolean get() = new.isEmpty() && changed.isEmpty() && cancelled.isEmpty() &&
        answeredElsewhere.isEmpty() && attendedChanged.isEmpty() && attendedCancelled.isEmpty() &&
        attendedDropped.isEmpty()

    /** These changes plus what happened to the events the user goes to. */
    fun withAttended(attended: AttendedChanges): InvitationChanges = copy(
        attendedChanged = attended.changed,
        attendedCancelled = attended.cancelled,
        attendedDropped = attended.dropped
    )
}
