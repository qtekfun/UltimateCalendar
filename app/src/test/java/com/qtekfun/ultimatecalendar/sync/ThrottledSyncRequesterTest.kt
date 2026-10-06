// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.sync.AccountSyncState
import com.qtekfun.ultimatecalendar.data.sync.AccountSyncTrigger
import com.qtekfun.ultimatecalendar.data.sync.DeviceSyncState
import com.qtekfun.ultimatecalendar.data.sync.ProviderSyncRequester
import com.qtekfun.ultimatecalendar.data.sync.SyncEnvironment
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import com.qtekfun.ultimatecalendar.data.sync.SyncRequestLog
import com.qtekfun.ultimatecalendar.data.sync.SyncRequests
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.now
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ThrottledSyncRequesterTest {
    private val google = CalendarAccount("me@gmail.com", "com.google")
    private val dav = CalendarAccount("me@cloud.example", "bitfire.at.davdroid")
    private val local = CalendarAccount("Device", "LOCAL")
    private val clock = MutableClock(now)
    private val asked = mutableListOf<Pair<CalendarAccount, Boolean>>()
    private val states = mutableMapOf<CalendarAccount, AccountSyncState>()
    private var device = DeviceSyncState(batterySaver = false, networkAvailable = true)
    private val log = MemoryLog()
    private val requester = ThrottledSyncRequester(
        ProviderSyncRequester({ account, urgent ->
            asked += account to urgent
        }, Dispatchers.Unconfined),
        object : SyncEnvironment {
            override fun account(account: CalendarAccount) =
                states[account] ?: AccountSyncState(syncable = true, syncsEvents = true)

            override fun device() = device
        },
        log,
        FixedCheckSettings(),
        clock
    )

    private class MemoryLog : SyncRequestLog {
        val times = mutableMapOf<CalendarAccount, Instant>()

        override fun lastRequest(account: CalendarAccount) = times[account]

        override fun record(accounts: Collection<CalendarAccount>, at: Instant) {
            accounts.forEach { times[it] = at }
        }
    }

    @Test
    fun `the first background run asks every account but the local one, without urgency`() =
        runTest {
            val result =
                requester.requestSync(setOf(google, dav, local), SyncReason.BACKGROUND)

            assertEquals(SyncRequests(requested = 2, failed = 0, skipped = 0), result)
            assertEquals(setOf(google to false, dav to false), asked.toSet())
            assertEquals(now, log.times[google])
            assertNull(log.times[local])
        }

    @Test
    fun `the next background run within the wait asks nobody`() = runTest {
        requester.requestSync(setOf(google, dav), SyncReason.BACKGROUND)
        asked.clear()
        clock.now = now.plus(Duration.ofMinutes(15))

        val result = requester.requestSync(setOf(google, dav), SyncReason.BACKGROUND)

        assertEquals(SyncRequests(requested = 0, failed = 0, skipped = 2), result)
        assertEquals(emptyList<Pair<CalendarAccount, Boolean>>(), asked)
        assertEquals(now, log.times[google])
    }

    @Test
    fun `a background run after the wait asks again and moves the record`() = runTest {
        requester.requestSync(setOf(google), SyncReason.BACKGROUND)
        clock.now = now.plus(Duration.ofMinutes(30))

        requester.requestSync(setOf(google), SyncReason.BACKGROUND)

        assertEquals(listOf(google to false, google to false), asked)
        assertEquals(clock.now, log.times[google])
    }

    @Test
    fun `each account is limited on its own`() = runTest {
        requester.requestSync(setOf(google), SyncReason.BACKGROUND)
        clock.now = now.plus(Duration.ofMinutes(10))
        asked.clear()

        val result = requester.requestSync(setOf(google, dav), SyncReason.BACKGROUND)

        assertEquals(SyncRequests(requested = 1, failed = 0, skipped = 1), result)
        assertEquals(listOf(dav to false), asked)
    }

    @Test
    fun `an account with sync off is skipped, in battery saver nobody is asked`() = runTest {
        states[google] = AccountSyncState(syncable = true, syncsEvents = false)
        requester.requestSync(setOf(google, dav), SyncReason.BACKGROUND)
        assertEquals(listOf(dav to false), asked)

        asked.clear()
        device = DeviceSyncState(batterySaver = true, networkAvailable = true)
        clock.now = now.plus(Duration.ofHours(2))
        val result = requester.requestSync(setOf(google, dav), SyncReason.BACKGROUND)

        assertEquals(SyncRequests(requested = 0, failed = 0, skipped = 2), result)
        assertEquals(emptyList<Pair<CalendarAccount, Boolean>>(), asked)
    }

    @Test
    fun `a manual refresh is urgent and ignores the last request`() = runTest {
        requester.requestSync(setOf(google), SyncReason.BACKGROUND)
        asked.clear()

        requester.requestSync(setOf(google), SyncReason.MANUAL)

        assertEquals(listOf(google to true), asked)
    }
}
