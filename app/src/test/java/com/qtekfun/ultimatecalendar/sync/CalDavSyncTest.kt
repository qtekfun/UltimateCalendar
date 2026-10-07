// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import androidx.work.ListenableWorker
import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionScheduler
import com.qtekfun.ultimatecalendar.data.sync.CompositeSyncRequester
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import com.qtekfun.ultimatecalendar.data.sync.SyncRequests
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.subscriptions.SchedulePlan
import com.qtekfun.ultimatecalendar.sync.engine.SyncEngine
import com.qtekfun.ultimatecalendar.sync.engine.SyncOutcome
import com.qtekfun.ultimatecalendar.sync.queue.ProcessResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecordingCalDavScheduler : CalDavSyncScheduler {
    val calls = mutableListOf<String>()

    override fun schedulePeriodic() {
        calls += "periodic"
    }

    override fun cancelAll() {
        calls += "cancel"
    }

    override fun syncSoon() {
        calls += "soon"
    }

    override fun syncNow() {
        calls += "now"
    }

    override fun syncPromptly() {
        calls += "promptly"
    }
}

class CalDavSyncWorkerTest {
    private val engine = mockk<SyncEngine>()
    private val worker = CalDavSyncWorker(mockk(relaxed = true), mockk(relaxed = true), engine)

    private fun verdict(outcome: SyncOutcome, attempt: Int = 0) =
        CalDavSyncWorker.verdict(outcome, attempt)

    @Test
    fun `a sync that sent everything is done`() {
        assertEquals(SyncVerdict.DONE, verdict(SyncOutcome.Ok(ProcessResult(done = 3))))
    }

    @Test
    fun `changes that could not be sent are tried again with backoff, a few times`() {
        val stuck = SyncOutcome.Ok(ProcessResult(done = 1, retried = 1))

        assertEquals(SyncVerdict.RETRY, verdict(stuck))
        assertEquals(SyncVerdict.RETRY, verdict(stuck, CalDavSyncWorker.MAX_ATTEMPTS - 1))
        assertEquals(SyncVerdict.DONE, verdict(stuck, CalDavSyncWorker.MAX_ATTEMPTS))
    }

    @Test
    fun `no connection and server errors are retried, a refused login or no account are not`() {
        assertEquals(SyncVerdict.RETRY, verdict(SyncOutcome.Offline))
        assertEquals(SyncVerdict.RETRY, verdict(SyncOutcome.Error("HTTP 500")))
        assertEquals(SyncVerdict.DONE, verdict(SyncOutcome.Offline, CalDavSyncWorker.MAX_ATTEMPTS))
        assertEquals(SyncVerdict.DONE, verdict(SyncOutcome.Unauthorized))
        assertEquals(SyncVerdict.DONE, verdict(SyncOutcome.NoAccount))
    }

    @Test
    fun `the worker syncs and reports success or retry`() = runTest {
        coEvery { engine.sync() } returns SyncOutcome.Ok(ProcessResult())
        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        coEvery { engine.sync() } returns SyncOutcome.Offline
        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalDavSyncStartTest {
    private val signedIn = SignedInAccount("https://cloud.example.com/", "ana")
    private val account = MutableStateFlow<SignedInAccount?>(null)
    private val session = mockk<AccountSession> {
        every { activeAccount } returns account
        every { restore() } returns null
    }
    private val scheduler = RecordingCalDavScheduler()
    private val engine = mockk<SyncEngine>()
    private val sync = CalDavSync(session, scheduler, engine, Dispatchers.Unconfined)

    @Test
    fun `nothing is scheduled while nobody is signed in, and everything stops at sign out`() =
        runTest {
            sync.start(backgroundScope)
            runCurrent()
            assertEquals(listOf("cancel"), scheduler.calls)

            account.value = signedIn
            runCurrent()
            assertEquals(listOf("cancel", "periodic", "now"), scheduler.calls)

            account.value = null
            runCurrent()
            assertEquals(listOf("cancel", "periodic", "now", "cancel"), scheduler.calls)
        }

    @Test
    fun `the login is restored first, so a background start finds the account`() = runTest {
        every { session.restore() } answers {
            account.value = signedIn
            signedIn
        }

        sync.start(backgroundScope)
        runCurrent()

        assertEquals(listOf("periodic", "now"), scheduler.calls)
    }

    @Test
    fun `opening the app syncs soon only with an account`() = runTest {
        sync.onAppOpened()
        assertEquals(emptyList<String>(), scheduler.calls)

        account.value = signedIn
        sync.onAppOpened()
        assertEquals(listOf("soon"), scheduler.calls)
    }

    @Test
    fun `syncing now is the engine's sync and its outcome is kept`() = runTest {
        coEvery { engine.sync() } returns SyncOutcome.Offline
        every { engine.lastOutcome } returns MutableStateFlow(SyncOutcome.Offline)

        assertEquals(SyncOutcome.Offline, sync.syncNow())
        assertEquals(SyncOutcome.Offline, sync.lastOutcome.value)
    }
}

class CompositeSyncRequesterTest {
    private val google = CalendarAccount("me@gmail.com", "com.google")
    private val cloud = CalendarAccount("ana@cloud.example.com", CalendarAccount.CALDAV_TYPE)
    private val provider = mockk<ThrottledSyncRequester>()
    private val scheduler = RecordingCalDavScheduler()
    private val feeds = RecordingSubscriptionScheduler()
    private val feedAccount =
        CalendarAccount("subscriptions", CalendarAccount.SUBSCRIPTION_TYPE)
    private val requester = CompositeSyncRequester(provider, scheduler, feeds)

    @Test
    fun `android accounts go to the system, the CalDAV account syncs when the user asks`() =
        runTest {
            coEvery { provider.requestSync(setOf(google), SyncReason.MANUAL) } returns
                SyncRequests(1, 0)

            val result = requester.requestSync(setOf(google, cloud), SyncReason.MANUAL)

            assertEquals(SyncRequests(2, 0), result)
            assertEquals(listOf("now"), scheduler.calls)
        }

    @Test
    fun `a background check leaves the CalDAV account to its own periodic sync`() = runTest {
        coEvery { provider.requestSync(setOf(google), SyncReason.BACKGROUND) } returns
            SyncRequests(1, 0)

        val result = requester.requestSync(setOf(google, cloud), SyncReason.BACKGROUND)

        assertEquals(SyncRequests(1, 0), result)
        assertEquals(emptyList<String>(), scheduler.calls)
    }

    @Test
    fun `without a CalDAV account the provider's answer is the answer and no sync is scheduled`() =
        runTest {
            coEvery { provider.requestSync(setOf(google), SyncReason.MANUAL) } returns
                SyncRequests(1, 2, skipped = 3)

            assertEquals(
                SyncRequests(1, 2, skipped = 3),
                requester.requestSync(setOf(google), SyncReason.MANUAL)
            )
            assertEquals(emptyList<String>(), scheduler.calls)
            coVerify(exactly = 1) { provider.requestSync(any(), any()) }
        }

    @Test
    fun `subscriptions refresh when the user asks and are never handed to the system`() = runTest {
        coEvery { provider.requestSync(setOf(google), SyncReason.MANUAL) } returns
            SyncRequests(1, 0)

        val result = requester.requestSync(setOf(google, feedAccount), SyncReason.MANUAL)

        assertEquals(SyncRequests(2, 0), result)
        assertEquals(1, feeds.refreshes)
        assertEquals(emptyList<String>(), scheduler.calls)
    }

    @Test
    fun `a background check leaves the subscriptions to their own periodic work`() = runTest {
        coEvery { provider.requestSync(emptySet(), SyncReason.BACKGROUND) } returns
            SyncRequests(0, 0)

        assertEquals(
            SyncRequests(0, 0),
            requester.requestSync(setOf(feedAccount), SyncReason.BACKGROUND)
        )
        assertEquals(0, feeds.refreshes)
    }

    @Test
    fun `CalDAV and subscriptions both start when both are asked`() = runTest {
        coEvery { provider.requestSync(emptySet(), SyncReason.MANUAL) } returns SyncRequests(0, 0)

        val result = requester.requestSync(setOf(cloud, feedAccount), SyncReason.MANUAL)

        assertEquals(SyncRequests(2, 0), result)
        assertEquals(listOf("now"), scheduler.calls)
        assertEquals(1, feeds.refreshes)
    }
}

class RecordingSubscriptionScheduler : SubscriptionScheduler {
    val plans = mutableListOf<SchedulePlan>()
    var refreshes = 0

    override fun apply(plan: SchedulePlan) {
        plans += plan
    }

    override fun refreshNow() {
        refreshes++
    }
}
