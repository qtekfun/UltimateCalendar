// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.sync.SourceSyncRequester
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.sync.RecordingSyncRequester
import java.time.Clock

/** Remembers the answers that were left waiting for an account to receive its copy. */
class RecordingRetry : PendingAnswerRetry {
    val scheduled = mutableListOf<Pair<InvitationKey, AttendeeStatus>>()

    override fun schedule(key: InvitationKey, status: AttendeeStatus) {
        scheduled += key to status
    }
}

/** The answers to the invitations for another account, over [source], with what they ask for. */
fun foreignAnswers(
    source: CalendarSource,
    requester: SourceSyncRequester = RecordingSyncRequester(),
    retry: PendingAnswerRetry = RecordingRetry(),
    clock: Clock = Clock.systemUTC()
) = ForeignAnswers(OwnCopies(source, clock), source, requester, retry)
