// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

/** How loudly an invitation notification shows. */
enum class InvitationAlert {
    /** A new invitation: it alerts, unless it is already on screen (a repeated check). */
    NEW,

    /** The organizer changed it: it alerts again, with the new details. */
    CHANGED,

    /** An unanswered invitation reminds again (T40): it alerts, with the details unchanged. */
    REMINDER,

    /** Updated in place, without sound or vibration. */
    SILENT
}

/** One thing to do to the notifications of the system. */
sealed interface NotificationOp {
    /** Shows, or replaces, the notification that asks for an answer. */
    data class ShowInvitation(val invitation: Invitation, val alert: InvitationAlert) :
        NotificationOp

    /** Removes it: the invitation was answered elsewhere or the event is gone. */
    data class CancelInvitation(val key: InvitationKey) : NotificationOp

    /** Tells, on the changes channel, that the organizer moved an invitation. */
    data class ShowMoved(val invitation: Invitation) : NotificationOp

    /** Tells, on the changes channel, that the organizer cancelled an event. */
    data class ShowCancelled(val invitation: Invitation) : NotificationOp

    /** Removes the notes of the changes channel about an event the user no longer goes to. */
    data class ClearChanges(val key: InvitationKey) : NotificationOp
}

/** Which optional notifications the user asked for in Settings (RF-07, both off by default). */
data class ChangeNotifications(val changes: Boolean, val cancellations: Boolean)

/** What to do with the group summary once the invitation notifications are as they should be. */
sealed interface SummaryOp {
    data class Show(val count: Int) : SummaryOp

    data object Cancel : SummaryOp
}

/**
 * Decides what the notifier does with a set of [InvitationChanges] (RF-07). Pure: the Android
 * calls are done by whoever applies the operations, so this is tested without a device.
 */
object InvitationNotificationPlanner {
    /** The smallest number of invitation notifications that are gathered under a summary. */
    const val MIN_FOR_SUMMARY = 2

    fun plan(changes: InvitationChanges, optional: ChangeNotifications): List<NotificationOp> =
        buildList {
            changes.new.forEach { add(NotificationOp.ShowInvitation(it, InvitationAlert.NEW)) }
            changes.changed.forEach { change ->
                // With "changes" on, the changes channel tells it and the invitation only updates.
                val alert = if (optional.changes) {
                    InvitationAlert.SILENT
                } else {
                    InvitationAlert.CHANGED
                }
                add(NotificationOp.ShowInvitation(change.current, alert))
                if (optional.changes) add(NotificationOp.ShowMoved(change.current))
            }
            changes.cancelled.forEach {
                add(NotificationOp.CancelInvitation(it.key))
                if (optional.cancellations) add(NotificationOp.ShowCancelled(it))
            }
            changes.answeredElsewhere.forEach { add(NotificationOp.CancelInvitation(it.key)) }
            // Events the user goes to (RF-07): nothing asks for an answer, only the notes remain.
            if (optional.changes) {
                changes.attendedChanged.forEach { add(NotificationOp.ShowMoved(it.current)) }
            }
            changes.attendedCancelled.forEach {
                add(NotificationOp.ClearChanges(it.key))
                if (optional.cancellations) add(NotificationOp.ShowCancelled(it))
            }
            changes.attendedDropped.forEach { add(NotificationOp.ClearChanges(it)) }
        }

    /** One notification needs no group; two or more are gathered under a summary. */
    fun summary(shown: Int): SummaryOp =
        if (shown >= MIN_FOR_SUMMARY) SummaryOp.Show(shown) else SummaryOp.Cancel
}
