// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import javax.inject.Inject
import javax.inject.Singleton

/** How an answer to an invitation went. */
sealed interface ResponseOutcome {
    /** The source stored the answer; it tells the organizer. */
    data object Answered : ResponseOutcome

    /** The event is gone (deleted or the user was removed): there is nothing left to answer. */
    data object Gone : ResponseOutcome

    /** The answer was not stored; the invitation is still pending. */
    data class Failed(val error: CalendarError) : ResponseOutcome

    /**
     * The invitation is for [address], another account of the user that has not received the event
     * yet. Nothing was written: the answer is kept and given when the event arrives, and the
     * invitation is still pending until then.
     */
    data class WaitingForAccount(val address: String) : ResponseOutcome
}

/** Answers invitations. The notification buttons and the tray both go through this. */
fun interface InvitationResponses {
    /** Safe to repeat: answering twice with the same [status] leaves the same result. */
    suspend fun respond(key: InvitationKey, status: AttendeeStatus): ResponseOutcome
}

/**
 * [InvitationResponses] over a [CalendarSource]; it never throws. An invitation for the account
 * of its own calendar is answered on its event. One for another of the user's accounts is
 * answered on that account's own copy, which [ForeignAnswers] finds (or waits for).
 */
@Singleton
class SourceInvitationResponses @Inject constructor(
    private val source: CalendarSource,
    private val foreign: ForeignAnswers
) : InvitationResponses {
    override suspend fun respond(key: InvitationKey, status: AttendeeStatus): ResponseOutcome =
        try {
            if (key.isForeign) foreign.answer(key, status) else source.answer(key.eventId, status)
        } catch (_: SecurityException) {
            // The calendar permission was revoked between the tap and the write.
            ResponseOutcome.Failed(CalendarError.PermissionDenied)
        }
}

/** Writes the answer to the event [id] and tells how it went. */
internal suspend fun CalendarSource.answer(id: EventId, status: AttendeeStatus): ResponseOutcome =
    when (val result = respond(id, status)) {
        is CalendarResult.Success -> ResponseOutcome.Answered

        is CalendarResult.Failure ->
            if (result.error == CalendarError.NotFound) {
                ResponseOutcome.Gone
            } else {
                ResponseOutcome.Failed(result.error)
            }
    }
