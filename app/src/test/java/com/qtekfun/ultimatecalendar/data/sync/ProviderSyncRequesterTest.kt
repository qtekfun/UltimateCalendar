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

    private fun requester(trigger: AccountSyncTrigger = AccountSyncTrigger { asked += it }) =
        ProviderSyncRequester(trigger, Dispatchers.Unconfined)

    @Test
    fun `every account with a sync adapter is asked, the local one is not`() = runTest {
        val result = requester().requestSync(setOf(google, dav, local))

        assertEquals(SyncRequests(requested = 2, failed = 0), result)
        assertEquals(setOf(google, dav), asked.toSet())
    }

    @Test
    fun `an account the system refuses does not stop the others`() = runTest {
        val refusing = AccountSyncTrigger {
            if (it == google) throw SecurityException("not allowed")
            require(it != dav) { "no such account" }
            asked += it
        }
        val other = CalendarAccount("x@y.z", "other")

        val result = requester(refusing).requestSync(setOf(google, dav, other))

        assertEquals(SyncRequests(requested = 1, failed = 2), result)
        assertEquals(listOf(other), asked)
    }

    @Test
    fun `no accounts means no requests`() = runTest {
        assertEquals(SyncRequests(0, 0), requester().requestSync(emptySet()))
    }
}
