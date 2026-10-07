// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.data.invitations.InvitationResponses
import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.invitations.ReplyStatus
import com.qtekfun.ultimatecalendar.data.invitations.ResponseOutcome
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAnswer
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import javax.inject.Inject
import javax.inject.Singleton

/** Runs a check that brings the invitation records, reminders and widgets up to date. */
fun interface InvitationRecheck {
    suspend fun run()
}

/**
 * What the Accept, Maybe and Decline buttons of an invitation notification do (RF-07). The answer
 * is written to the source; when it is stored, or the event no longer exists, the notification
 * goes. When it fails the notification stays, with a line saying so, so the user can try again:
 * the buttons are safe to press twice. A button pressed after the process died simply runs
 * again from the notification that is still on screen.
 *
 * A stored answer is announced honestly: "Accepted", and when its account cannot sync right now,
 * that the reply goes out when it syncs (the source schedules the retry). It is followed by a
 * check, so that the record of notified invitations and the extra reminders no longer count the
 * invitation as pending without waiting for the provider's change notification.
 */
@Singleton
class InvitationActionHandler @Inject constructor(
    private val responses: InvitationResponses,
    private val surface: InvitationNotificationSurface,
    private val notified: NotifiedInvitations,
    private val status: ReplyStatus,
    private val recheck: InvitationRecheck
) {
    suspend fun answer(key: InvitationKey, answer: InvitationAnswer) {
        when (responses.respond(key, answer.status)) {
            ResponseOutcome.Answered -> {
                surface.cancel(key)
                surface.refreshSummary()
                surface.showAnswered(answer, status.isWaiting(key))
                recheck.run()
            }

            ResponseOutcome.Gone -> {
                surface.cancel(key)
                surface.refreshSummary()
                recheck.run()
            }

            is ResponseOutcome.Failed ->
                notified.load().firstOrNull { it.key == key }?.let(surface::showAnswerFailed)
        }
    }
}
