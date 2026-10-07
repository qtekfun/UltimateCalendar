// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.data.subscriptions.RefreshSummary
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionsRefresh
import com.qtekfun.ultimatecalendar.data.sync.AccountSyncState
import com.qtekfun.ultimatecalendar.data.sync.DeviceSyncState
import com.qtekfun.ultimatecalendar.data.sync.SyncEnvironment
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.refresh.RefreshIssue
import com.qtekfun.ultimatecalendar.domain.refresh.RefreshReport
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.calendar
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.now
import com.qtekfun.ultimatecalendar.sync.engine.SyncOutcome
import com.qtekfun.ultimatecalendar.sync.queue.ProcessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@OptIn(ExperimentalCoroutinesApi::class)
class ManualRefreshTest {
    private val clock = MutableClock(now)
    private val requests = RecordingSyncRequester()
    private val dav = CalendarAccount("me@cloud.example", "bitfire.at.davdroid")
    private lateinit var database: UltimateCalendarDatabase
    private var calendars: CalendarSource = FakeCalendarSource(listOf(calendar(1)))
    private var device = DeviceSyncState(batterySaver = false, networkAvailable = true)
    private val accountStates = mutableMapOf<CalendarAccount, AccountSyncState>()
    private var ownCalls = 0
    private var feedCalls = 0
    private var ownOutcome: suspend () -> SyncOutcome = {
        SyncOutcome.Ok(ProcessResult())
    }
    private var feedOutcome: suspend () -> RefreshSummary = { RefreshSummary(2, 0) }

    @BeforeEach
    fun open() {
        database = inMemoryDatabase()
    }

    @AfterEach
    fun close() = database.close()

    private fun TestScope.refresh(): ManualRefresh {
        val checker = InvitationChecker(
            calendars,
            requests,
            NotifiedInvitations(database.notifiedInvitationDao(), Dispatchers.Unconfined),
            RecordingNotifier(),
            FixedCheckSettings(),
            clock,
            Dispatchers.Unconfined
        )
        return ManualRefresh(
            checker,
            calendars,
            {
                ownCalls++
                ownOutcome()
            },
            SubscriptionsRefresh {
                feedCalls++
                feedOutcome()
            },
            object : SyncEnvironment {
                override fun account(account: CalendarAccount) =
                    accountStates[account] ?: AccountSyncState(syncable = true, syncsEvents = true)

                override fun device() = device
            },
            StandardTestDispatcher(testScheduler)
        )
    }

    private suspend fun ManualRefresh.report(): RefreshReport = requireNotNull(refresh())

    @Test
    fun `everything is started on its manual path and a clean run is up to date`() = runTest {
        val report = refresh().report()

        assertTrue(report.upToDate)
        assertEquals(listOf(SyncReason.MANUAL), requests.reasons)
        assertEquals(listOf(setOf(CheckFixtures.account)), requests.requests)
        assertEquals(1, ownCalls)
        assertEquals(1, feedCalls)
    }

    @Test
    fun `offline it answers at once, starts no server work and still reads the phone`() = runTest {
        device = DeviceSyncState(batterySaver = false, networkAvailable = false)

        val report = refresh().report()

        assertEquals(RefreshReport(setOf(RefreshIssue.OFFLINE)), report)
        assertEquals(0, ownCalls)
        assertEquals(0, feedCalls)
        assertEquals(1, requests.requests.size)
    }

    @Test
    fun `a missing calendar permission is reported`() = runTest {
        val denied = FakeCalendarSource(listOf(calendar(1)))
        calendars = object : CalendarSource by denied {
            override suspend fun calendars() =
                CalendarResult.Failure(CalendarError.PermissionDenied)
        }

        assertEquals(
            RefreshReport(setOf(RefreshIssue.PERMISSION_MISSING)),
            refresh().report()
        )
    }

    @Test
    fun `calendars that cannot be read for another reason are reported as such`() = runTest {
        val broken = FakeCalendarSource(listOf(calendar(1)))
        calendars = object : CalendarSource by broken {
            override suspend fun calendars() =
                CalendarResult.Failure(CalendarError.SourceFailure("gone"))
        }

        assertEquals(RefreshReport(setOf(RefreshIssue.READ_FAILED)), refresh().report())
    }

    @Test
    fun `the CalDAV account's answers become issues`() = runTest {
        val outcomes = mapOf(
            SyncOutcome.Offline to setOf(RefreshIssue.OFFLINE),
            SyncOutcome.Unauthorized to setOf(RefreshIssue.SIGN_IN_REFUSED),
            SyncOutcome.Error("HTTP 503") to setOf(RefreshIssue.SERVER_ERROR),
            SyncOutcome.NoAccount to emptySet<RefreshIssue>()
        )
        outcomes.forEach { (outcome, issues) ->
            ownOutcome = { outcome }
            assertEquals(RefreshReport(issues), refresh().report(), outcome.toString())
        }
    }

    @Test
    fun `a subscription that could not be downloaded is a server error`() = runTest {
        feedOutcome = { RefreshSummary(attempted = 3, temporaryFailures = 1) }

        assertEquals(RefreshReport(setOf(RefreshIssue.SERVER_ERROR)), refresh().report())
    }

    @Test
    fun `an account of the phone that cannot sync is reported, the others are not asked about`() =
        runTest {
            val calendarsOf = FakeCalendarSource(
                listOf(
                    calendar(1),
                    CalendarInfo(
                        CalendarId(2),
                        CalendarAccount("Device", "LOCAL"),
                        "Device",
                        0,
                        CalendarAccess.OWNER
                    ),
                    CalendarInfo(
                        CalendarId(3),
                        dav,
                        "Dav",
                        0,
                        CalendarAccess.OWNER
                    )
                )
            )
            calendars = calendarsOf
            accountStates[CalendarAccount("Device", "LOCAL")] =
                AccountSyncState(syncable = false, syncsEvents = false)
            assertTrue(refresh().report().upToDate)

            accountStates[dav] = AccountSyncState(syncable = false, syncsEvents = false)

            assertEquals(
                RefreshReport(setOf(RefreshIssue.ACCOUNT_SYNC_OFF)),
                refresh().report()
            )
        }

    @Test
    fun `a server that never answers is given up on, reported, and the button is free again`() =
        runTest {
            ownOutcome = { awaitCancellation() }
            val manual = refresh()

            assertEquals(RefreshReport(setOf(RefreshIssue.SERVER_ERROR)), manual.report())

            assertFalse(manual.running.value)
        }

    @Test
    fun `a second tap while one refresh runs does nothing`() = runTest {
        val release = CompletableDeferred<Unit>()
        ownOutcome = {
            release.await()
            SyncOutcome.Ok(ProcessResult())
        }
        val manual = refresh()

        val first = backgroundScope.launch { manual.refresh() }
        runCurrent()
        assertTrue(manual.running.value)
        assertNull(manual.refresh())

        release.complete(Unit)
        runCurrent()
        first.join()

        assertFalse(manual.running.value)
        assertEquals(1, ownCalls)
    }

    @Test
    fun `a failure inside a refresh still ends it`() = runTest {
        ownOutcome = { error("boom") }
        val manual = refresh()

        assertThrows<IllegalStateException> { manual.refresh() }
        assertFalse(manual.running.value)

        ownOutcome = { SyncOutcome.Ok(ProcessResult()) }
        assertTrue(manual.report().upToDate)
    }
}
