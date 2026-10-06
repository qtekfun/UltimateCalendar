// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
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
}

/** Answers invitations. The notification buttons and the tray both go through this. */
fun interface InvitationResponses {
    /** Safe to repeat: answering twice with the same [status] leaves the same result. */
    suspend fun respond(key: InvitationKey, status: AttendeeStatus): ResponseOutcome
}

/** [InvitationResponses] over a [CalendarSource]; it never throws. */
@Singleton
class SourceInvitationResponses @Inject constructor(private val source: CalendarSource) :
    InvitationResponses {
    override suspend fun respond(key: InvitationKey, status: AttendeeStatus): ResponseOutcome =
        try {
            when (val result = source.respond(key.eventId, status)) {
                is CalendarResult.Success -> ResponseOutcome.Answered

                is CalendarResult.Failure ->
                    if (result.error == CalendarError.NotFound) {
                        ResponseOutcome.Gone
                    } else {
                        ResponseOutcome.Failed(result.error)
                    }
            }
        } catch (_: SecurityException) {
            // The calendar permission was revoked between the tap and the write.
            ResponseOutcome.Failed(CalendarError.PermissionDenied)
        }
}
