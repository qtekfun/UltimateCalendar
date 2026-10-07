// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InvitationSyncsTest {
    private val google = CalendarAccount("me@gmail.com", "com.google")
    private val dav = CalendarAccount("me@cloud.example", "bitfire.at.davdroid")
    private val asked = mutableListOf<Pair<Set<CalendarAccount>, SyncReason>>()
    private var answer = SyncRequests(requested = 1, failed = 0)

    private val requester = object : SourceSyncRequester {
        override suspend fun requestSync(accounts: Set<CalendarAccount>, reason: SyncReason) =
            answer.also { asked += accounts to reason }
    }

    @Test
    fun `a burst of writes to one account is one expedited request after the wait`() = runTest {
        val syncs = InvitationSyncs(requester, backgroundScope, debounceMs = 1_500)

        syncs.written(google)
        advanceTimeBy(1_000)
        syncs.written(google)
        syncs.written(google)
        advanceTimeBy(499)
        runCurrent()
        assertEquals(emptyList<Any>(), asked, "nothing is asked before the wait is over")

        advanceTimeBy(2)
        runCurrent()

        assertEquals(listOf(setOf(google) to SyncReason.WRITE), asked)
    }

    @Test
    fun `a write after the request starts a new wait`() = runTest {
        val syncs = InvitationSyncs(requester, backgroundScope, debounceMs = 1_500)
        syncs.written(google)
        advanceTimeBy(1_600)
        runCurrent()

        syncs.written(google)
        advanceTimeBy(1_600)
        runCurrent()

        assertEquals(2, asked.size)
    }

    @Test
    fun `each account is asked on its own`() = runTest {
        val syncs = InvitationSyncs(requester, backgroundScope, debounceMs = 1_500)

        syncs.written(google)
        syncs.written(dav)
        advanceTimeBy(1_600)
        runCurrent()

        assertEquals(setOf(setOf(google), setOf(dav)), asked.map { it.first }.toSet())
        assertEquals(2, asked.size)
    }

    @Test
    fun `the on-device, CalDAV and subscription accounts are never asked`() = runTest {
        val syncs = InvitationSyncs(requester, backgroundScope, debounceMs = 1_500)

        syncs.written(CalendarAccount("Device", "LOCAL"))
        syncs.written(CalendarAccount("me", CalendarAccount.CALDAV_TYPE))
        syncs.written(CalendarAccount("feeds", CalendarAccount.SUBSCRIPTION_TYPE))
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(emptyList<Any>(), asked)
    }

    @Test
    fun `a request the system could not take is told, one that was taken is not`() = runTest {
        val syncs = InvitationSyncs(requester, backgroundScope, debounceMs = 1_500)
        syncs.unsent.test {
            syncs.written(google)
            advanceTimeBy(1_600)
            runCurrent()
            expectNoEvents()

            answer = SyncRequests(requested = 0, failed = 0, skipped = 1)
            syncs.written(google)
            advanceTimeBy(1_600)
            runCurrent()

            awaitItem()
            expectNoEvents()
        }
    }
}
