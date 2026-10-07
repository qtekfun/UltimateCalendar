// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.sync.ReplyDelivery
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import javax.inject.Inject

/** Tells whether an answer just given to an invitation still has to wait for its account to sync. */
fun interface ReplyStatus {
    suspend fun isWaiting(key: InvitationKey): Boolean
}

/** [ReplyStatus] from the account of the invitation's calendar; unknown means not waiting. */
class SourceReplyStatus @Inject constructor(
    private val source: CalendarSource,
    private val delivery: ReplyDelivery
) : ReplyStatus {
    override suspend fun isWaiting(key: InvitationKey): Boolean {
        val calendars = (source.calendars() as? CalendarResult.Success)?.value
        val account = calendars?.firstOrNull { it.id == key.calendarId }?.account
        return account != null && delivery.isWaiting(account)
    }
}
