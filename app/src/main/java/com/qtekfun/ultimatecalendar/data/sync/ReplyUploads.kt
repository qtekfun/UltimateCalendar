// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.EventId
import javax.inject.Inject

/** Where the user's answer to an event stands while it has not reached the organizer. */
enum class ReplyUpload {
    /** The account can sync and was asked to; it has not uploaded the answer yet. */
    SENDING,

    /** The account cannot sync now (no network, calendar sync off): it goes when it can. */
    WAITING
}

/** What the event detail says about an answer still on the phone, and its "send now". */
interface ReplyUploads {
    /** Null when nothing of the event [id] waits to be uploaded, or when it cannot be told. */
    suspend fun state(id: EventId, account: CalendarAccount): ReplyUpload?

    /** Asks [account] to sync now, as the user asked, and follows the answer again. */
    suspend fun sendNow(account: CalendarAccount)
}

/**
 * [ReplyUploads] from the provider's upload mark ([PendingUploads]): the answer is shown as on its
 * way only while it really is, and the reason when the account cannot send it.
 */
class ProviderReplyUploads @Inject constructor(
    private val pending: PendingUploads,
    private val delivery: ReplyDelivery,
    private val requester: SourceSyncRequester
) : ReplyUploads {
    override suspend fun state(id: EventId, account: CalendarAccount): ReplyUpload? = when {
        !pending.isPending(id) -> null
        delivery.isWaiting(account) -> ReplyUpload.WAITING
        else -> ReplyUpload.SENDING
    }

    override suspend fun sendNow(account: CalendarAccount) {
        requester.requestSync(setOf(account), SyncReason.MANUAL)
        delivery.follow(account)
    }
}
