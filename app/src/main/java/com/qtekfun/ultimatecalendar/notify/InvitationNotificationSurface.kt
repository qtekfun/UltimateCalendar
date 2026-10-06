// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAlert
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey

/**
 * The notifications of invitations as the system shows them (RF-07). Everything that decides
 * what to show or remove lives in `domain.invitations` and in [SystemInvitationNotifier]; an
 * implementation only talks to Android, and shows nothing, silently, while the notification
 * permission is missing. It never throws.
 */
interface InvitationNotificationSurface {
    /** Shows, or replaces, the notification that asks for an answer, with its three buttons. */
    fun show(invitation: Invitation, alert: InvitationAlert)

    /** Like [show], with a line saying the last answer was not sent. */
    fun showAnswerFailed(invitation: Invitation)

    /** Tells, on the changes channel, that the organizer moved the event. */
    fun showMoved(invitation: Invitation)

    /** Tells, on the changes channel, that the organizer cancelled the event. */
    fun showCancelled(invitation: Invitation)

    /** Removes the notes on the changes channel about an event (moved, cancelled), if shown. */
    fun clearChanges(key: InvitationKey)

    /** Removes the notification that asks for an answer, if it is on screen. */
    fun cancel(key: InvitationKey)

    /** Shows the group summary while two or more notifications ask for an answer, else removes it. */
    fun refreshSummary()
}
