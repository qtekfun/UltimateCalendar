// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.account

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavAccountRepository
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavAccountState
import com.qtekfun.ultimatecalendar.domain.caldav.CalDavCalendarItem
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.sync.engine.SyncOutcome
import com.qtekfun.ultimatecalendar.sync.engine.SyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalDavAccountViewModelTest {
    private val account = SignedInAccount("https://cloud.example.com/", "ana")
    private val signedIn = CalDavAccountState.SignedIn(account, 2, emptyList(), true, 0, 0)
    private val accountState = MutableStateFlow<CalDavAccountState>(CalDavAccountState.SignedOut)
    private val calendars = MutableStateFlow<List<CalDavCalendarItem>>(emptyList())
    private val status = MutableStateFlow(SyncStatus(SyncStatus.Phase.NEVER, null))
    private val repository = mockk<CalDavAccountRepository> {
        every { state } returns accountState
        every { this@mockk.calendars } returns this@CalDavAccountViewModelTest.calendars
        every { syncStatus } returns status
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private val work = CalDavCalendarItem(CalendarId(1), "Work", 0xFF0000FF.toInt(), true, true)

    @Test
    fun `nothing is known until the account is read, then signed out means the login`() = runTest {
        val model = CalDavAccountViewModel(repository)
        // Before anything is read, neither the login nor the account is chosen.
        assertNull(model.state.value.account)
        assertFalse(model.state.value.signedOut)

        model.state.test {
            var latest = awaitItem()
            while (latest.account == null) latest = awaitItem()
            assertTrue(latest.signedOut)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the account, its calendars and the sync status come together`() = runTest {
        val model = CalDavAccountViewModel(repository)
        val at = Instant.parse("2026-10-06T08:00:00Z")

        model.state.test {
            accountState.value = signedIn
            calendars.value = listOf(work)
            status.value = SyncStatus(SyncStatus.Phase.OK, at)

            var latest = awaitItem()
            while (latest.account != signedIn || latest.calendars != listOf(work) ||
                latest.status.lastOk != at
            ) {
                latest = awaitItem()
            }
            assertFalse(latest.signedOut)
            assertFalse(latest.signingOut)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sync now asks the repository for a manual sync`() = runTest {
        coEvery { repository.syncNow() } returns SyncOutcome.Offline

        CalDavAccountViewModel(repository).syncNow()

        coVerify(exactly = 1) { repository.syncNow() }
    }

    @Test
    fun `a calendar is switched through the repository`() = runTest {
        coEvery { repository.setCalendarEnabled(any(), any()) } returns Unit
        val model = CalDavAccountViewModel(repository)

        model.setEnabled(CalendarId(1), false)
        model.setEnabled(CalendarId(1), true)

        coVerify { repository.setCalendarEnabled(CalendarId(1), false) }
        coVerify { repository.setCalendarEnabled(CalendarId(1), true) }
    }

    @Test
    fun `signing out shows it is under way, runs once and ends`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.signOut() } coAnswers { gate.await() }
        val model = CalDavAccountViewModel(repository)

        model.state.test {
            assertFalse(awaitItem().signingOut)
            model.signOut()
            // A second tap while it runs does nothing.
            model.signOut()
            var latest = awaitItem()
            while (!latest.signingOut) latest = awaitItem()

            gate.complete(Unit)
            while (latest.signingOut) latest = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { repository.signOut() }
    }
}
