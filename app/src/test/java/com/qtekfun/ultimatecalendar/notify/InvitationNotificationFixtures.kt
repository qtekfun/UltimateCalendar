// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.data.invitations.InvitationResponses
import com.qtekfun.ultimatecalendar.data.invitations.ResponseOutcome
import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAlert
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.ZoneOffset

/** An invitation with invented data. */
fun sampleInvitation(id: Long, title: String = "Event $id", calendar: Long = 1) = Invitation(
    key = InvitationKey(CalendarId(calendar), EventId(id)),
    title = title,
    time = EventTime.Timed(
        Instant.parse("2026-06-10T10:00:00Z"),
        Instant.parse("2026-06-10T11:00:00Z"),
        ZoneOffset.UTC
    )
)

/** What the notifier or a button did to the notifications, in order. */
class RecordingSurface : InvitationNotificationSurface {
    val calls = mutableListOf<String>()
    val alerts = mutableMapOf<InvitationKey, InvitationAlert>()

    override fun show(invitation: Invitation, alert: InvitationAlert) {
        alerts[invitation.key] = alert
        calls += "show ${invitation.key.eventId.value}"
    }

    override fun showAnswerFailed(invitation: Invitation) {
        calls += "failed ${invitation.key.eventId.value}"
    }

    override fun showMoved(invitation: Invitation) {
        calls += "moved ${invitation.key.eventId.value}"
    }

    override fun showCancelled(invitation: Invitation) {
        calls += "cancelled ${invitation.key.eventId.value}"
    }

    override fun clearChanges(key: InvitationKey) {
        calls += "clear ${key.eventId.value}"
    }

    override fun cancel(key: InvitationKey) {
        calls += "cancel ${key.eventId.value}"
    }

    override fun refreshSummary() {
        calls += "summary"
    }
}

/** Answers with a fixed outcome and remembers what it was asked. */
class FixedResponses(var outcome: ResponseOutcome) : InvitationResponses {
    val asked = mutableListOf<Pair<InvitationKey, AttendeeStatus>>()

    override suspend fun respond(key: InvitationKey, status: AttendeeStatus): ResponseOutcome {
        asked += key to status
        return outcome
    }
}
