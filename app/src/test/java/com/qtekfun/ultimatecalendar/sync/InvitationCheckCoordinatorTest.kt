// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.calendar
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.invitation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InvitationCheckCoordinatorTest {
    private val source = FakeCalendarSource(listOf(calendar()))
    private val notifier = RecordingNotifier()
    private val syncs = RecordingSyncRequester()
    private val settings = FixedCheckSettings()
    private val scheduler = RecordingScheduler()

    private fun TestScope.coordinator(): InvitationCheckCoordinator {
        val io = UnconfinedTestDispatcher(testScheduler)
        val checker = InvitationChecker(
            source,
            syncs,
            NotifiedInvitations(InMemoryNotifiedDao(), io),
            notifier,
            settings,
            MutableClock(CheckFixtures.now),
            io
        )
        return InvitationCheckCoordinator(checker, scheduler, settings)
    }

    @Test
    fun `starting schedules the job for the interval in settings`() = runTest {
        settings.interval = CheckInterval.HOUR

        coordinator().start(backgroundScope)
        runCurrent()

        assertEquals(listOf(CheckInterval.HOUR), scheduler.applied)
    }

    @Test
    fun `changing the interval replaces the job, manual only included`() = runTest {
        val coordinator = coordinator()

        settings.interval = CheckInterval.HALF_HOUR
        coordinator.intervalChanged()
        settings.interval = CheckInterval.MANUAL_ONLY
        coordinator.intervalChanged()

        assertEquals(listOf(CheckInterval.HALF_HOUR, CheckInterval.MANUAL_ONLY), scheduler.applied)
    }

    @Test
    fun `a burst of provider changes is one check, and it does not ask for a sync`() = runTest {
        coordinator().start(backgroundScope)
        runCurrent()

        source.create(invitation("A"))
        advanceTimeBy(2_000)
        source.create(invitation("B"))
        advanceTimeBy(4_000)
        runCurrent()
        assertTrue(notifier.calls.isEmpty())

        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(1, notifier.calls.size)
        assertEquals(setOf("A", "B"), notifier.calls.single().new.map { it.title }.toSet())
        assertTrue(syncs.requests.isEmpty())
    }

    @Test
    fun `opening the app checks once after asking the accounts to sync`() = runTest {
        source.create(invitation("A"))
        coordinator().also { it.start(backgroundScope) }.apply {
            runCurrent()
            onAppOpened()
            onAppOpened()
            advanceTimeBy(500)
            runCurrent()
            assertTrue(notifier.calls.isEmpty())
            advanceTimeBy(600)
            runCurrent()
        }

        assertEquals(1, notifier.calls.size)
        assertEquals(1, syncs.requests.size)
    }

    @Test
    fun `checking now runs at once, asks for a sync and reports the result`() = runTest {
        source.create(invitation("A"))

        val outcome = coordinator().checkNow()

        assertEquals(1, (outcome as InvitationCheckOutcome.Done).pending)
        assertEquals(1, syncs.requests.size)
        assertEquals(1, notifier.calls.size)
    }
}
