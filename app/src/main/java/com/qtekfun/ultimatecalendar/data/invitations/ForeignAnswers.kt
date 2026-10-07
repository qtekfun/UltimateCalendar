// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.sync.SourceSyncRequester
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import javax.inject.Inject

/**
 * Asks, later and when the phone has a connection, for another try at an answer that waits for
 * the invited account to receive its copy of the event. It must survive the process dying and be
 * bounded: after its last try it calls [ForeignAnswers.attempt] with `last` set and stops.
 */
fun interface PendingAnswerRetry {
    fun schedule(key: InvitationKey, status: AttendeeStatus)
}

/** What one try at a waiting answer came to. */
enum class AnswerAttempt {
    /** The answer was written to the account's own copy. */
    ANSWERED,

    /** The event is gone: nothing left to answer. */
    GONE,

    /** The copy has not arrived and tries are left: the account was asked to sync again. */
    RETRY,

    /** The copy never arrived or could not be answered, and there are no more tries. */
    GAVE_UP
}

/**
 * Answers an invitation addressed to another account of the user (A organises and invites B,
 * both are the user's). Google records an answer only from the invited account, so it is written
 * to that account's own copy of the event, never to the organizer's. When that copy has not
 * arrived (the account's sync has not delivered it, or the account keeps invitations out of its
 * calendar), nothing is written and nothing is pretended: the account is asked to sync urgently
 * and the answer is kept by [retry] until the copy shows up, a bounded number of times.
 */
class ForeignAnswers @Inject constructor(
    private val copies: OwnCopies,
    private val source: CalendarSource,
    private val syncs: SourceSyncRequester,
    private val retry: PendingAnswerRetry
) {
    /** The user's answer: written now when the copy exists, else kept and retried. */
    suspend fun answer(key: InvitationKey, status: AttendeeStatus): ResponseOutcome =
        when (val found = copies.find(key)) {
            is CopyLookup.Found -> source.answer(found.eventId, status)

            is CopyLookup.Missing -> {
                syncs.requestSync(found.accounts, SyncReason.MANUAL)
                retry.schedule(key, status)
                ResponseOutcome.WaitingForAccount(key.address)
            }

            CopyLookup.Gone -> ResponseOutcome.Gone

            is CopyLookup.Failed -> ResponseOutcome.Failed(found.error)
        }

    /** One try of the retry job; [last] when no more will follow. */
    suspend fun attempt(key: InvitationKey, status: AttendeeStatus, last: Boolean): AnswerAttempt =
        when (val found = copies.find(key)) {
            is CopyLookup.Found -> settled(source.answer(found.eventId, status), last)
            is CopyLookup.Missing -> if (last) AnswerAttempt.GAVE_UP else asked(found.accounts)
            CopyLookup.Gone -> AnswerAttempt.GONE
            is CopyLookup.Failed -> if (last) AnswerAttempt.GAVE_UP else AnswerAttempt.RETRY
        }

    private suspend fun asked(accounts: Set<CalendarAccount>): AnswerAttempt {
        syncs.requestSync(accounts, SyncReason.MANUAL)
        return AnswerAttempt.RETRY
    }

    private fun settled(outcome: ResponseOutcome, last: Boolean): AnswerAttempt = when (outcome) {
        ResponseOutcome.Answered -> AnswerAttempt.ANSWERED
        ResponseOutcome.Gone -> AnswerAttempt.GONE
        else -> if (last) AnswerAttempt.GAVE_UP else AnswerAttempt.RETRY
    }
}
