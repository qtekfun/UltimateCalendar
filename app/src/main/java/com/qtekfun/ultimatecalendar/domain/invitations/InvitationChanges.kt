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
    val answeredElsewhere: List<Invitation>
) {
    val isEmpty: Boolean get() = new.isEmpty() && changed.isEmpty() && cancelled.isEmpty() &&
        answeredElsewhere.isEmpty()
}
