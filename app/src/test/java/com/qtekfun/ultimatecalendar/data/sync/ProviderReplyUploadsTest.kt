// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.EventId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProviderReplyUploadsTest {
    private val google = CalendarAccount("me@gmail.com", "com.google")
    private val id = EventId(7)
    private var marked = true
    private var network = true
    private var follows = 0
    private val asked = mutableListOf<Pair<Set<CalendarAccount>, SyncReason>>()

    private val uploads = ProviderReplyUploads(
        object : PendingUploads {
            override suspend fun accounts() = if (marked) setOf(google) else emptySet()

            override suspend fun isPending(id: EventId) = marked && id == EventId(7)
        },
        ReplyDelivery(
            object : SyncEnvironment {
                override fun account(account: CalendarAccount) = AccountSyncState(true, true)

                override fun device() = DeviceSyncState(false, network)
            },
            { follows++ }
        ),
        object : SourceSyncRequester {
            override suspend fun requestSync(accounts: Set<CalendarAccount>, reason: SyncReason) =
                SyncRequests(accounts.size, 0).also { asked += accounts to reason }
        }
    )

    @Test
    fun `a marked answer is on its way, or waiting when the account cannot sync`() = runTest {
        assertEquals(ReplyUpload.SENDING, uploads.state(id, google))
        network = false
        assertEquals(ReplyUpload.WAITING, uploads.state(id, google))
    }

    @Test
    fun `an answer already uploaded, or another event, has nothing on its way`() = runTest {
        assertEquals(null, uploads.state(EventId(8), google))
        marked = false
        assertEquals(null, uploads.state(id, google))
    }

    @Test
    fun `send now asks the account as the user would and follows the answer again`() = runTest {
        uploads.sendNow(google)
        assertEquals(listOf(setOf(google) to SyncReason.MANUAL), asked)
        assertEquals(1, follows)
    }
}
