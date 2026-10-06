// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProviderSyncRequesterTest {
    private val google = CalendarAccount("me@gmail.com", "com.google")
    private val dav = CalendarAccount("me@cloud.example", "bitfire.at.davdroid")
    private val local = CalendarAccount("Device", "LOCAL")
    private val asked = mutableListOf<CalendarAccount>()
    private val urgent = mutableListOf<Boolean>()

    private fun requester(
        trigger: AccountSyncTrigger = AccountSyncTrigger { account, expedited ->
            asked += account
            urgent += expedited
        }
    ) = ProviderSyncRequester(trigger, Dispatchers.Unconfined)

    @Test
    fun `every account with a sync adapter is asked, the local one is not`() = runTest {
        val result = requester().requestSync(setOf(google, dav, local), SyncReason.BACKGROUND)

        assertEquals(SyncRequests(requested = 2, failed = 0), result)
        assertEquals(setOf(google, dav), asked.toSet())
    }

    @Test
    fun `only a manual request is expedited`() = runTest {
        requester().requestSync(setOf(google), SyncReason.BACKGROUND)
        requester().requestSync(setOf(google), SyncReason.MANUAL)

        assertEquals(listOf(false, true), urgent)
    }

    @Test
    fun `an account the system refuses does not stop the others`() = runTest {
        val refusing = AccountSyncTrigger { account, _ ->
            if (account == google) throw SecurityException("not allowed")
            require(account != dav) { "no such account" }
            asked += account
        }
        val other = CalendarAccount("x@y.z", "other")

        val result =
            requester(refusing).requestSync(setOf(google, dav, other), SyncReason.BACKGROUND)

        assertEquals(SyncRequests(requested = 1, failed = 2), result)
        assertEquals(listOf(other), asked)
    }

    @Test
    fun `no accounts means no requests`() = runTest {
        assertEquals(
            SyncRequests(0, 0),
            requester().requestSync(emptySet(), SyncReason.BACKGROUND)
        )
    }
}
