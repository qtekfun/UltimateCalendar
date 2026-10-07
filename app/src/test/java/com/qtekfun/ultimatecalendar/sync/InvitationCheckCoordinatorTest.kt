// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationChanges
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationNotifier
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.calendar
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.invitation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
        coordinator().start(backgroundScope)
        runCurrent()

        settings.interval = CheckInterval.HALF_HOUR
        runCurrent()
        settings.interval = CheckInterval.MANUAL_ONLY
        runCurrent()

        assertEquals(
            listOf(CheckInterval.QUARTER_HOUR, CheckInterval.HALF_HOUR, CheckInterval.MANUAL_ONLY),
            scheduler.applied
        )
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
    fun `what was written before the watching began is found by its first check`() = runTest {
        // The provider's observer is registered a moment after the process starts: a write in that
        // gap raises a change nobody hears, and nothing else would look until the next job run.
        source.create(invitation("A"))

        coordinator().start(backgroundScope)
        advanceTimeBy(4_000)
        runCurrent()
        assertTrue(notifier.calls.isEmpty())
        advanceTimeBy(1_100)
        runCurrent()

        assertEquals(listOf("A"), notifier.calls.single().new.map { it.title })
        assertTrue(syncs.requests.isEmpty())
    }

    @Test
    fun `a change that arrives while a check runs is checked right after it`() = runTest {
        val io = UnconfinedTestDispatcher(testScheduler)
        val slow = object : InvitationNotifier {
            val calls = mutableListOf<List<String>>()

            override suspend fun notify(changes: InvitationChanges) {
                calls += changes.new.map { it.title }
                if (calls.size == 1) delay(10_000)
            }
        }
        val checker = InvitationChecker(
            source,
            syncs,
            NotifiedInvitations(InMemoryNotifiedDao(), io),
            slow,
            settings,
            MutableClock(CheckFixtures.now),
            io
        )
        InvitationCheckCoordinator(checker, scheduler, settings).start(backgroundScope)
        source.create(invitation("A"))
        advanceTimeBy(5_100)
        runCurrent()
        assertEquals(listOf(listOf("A")), slow.calls)

        // The first check is still in its notifier: B arrives meanwhile.
        advanceTimeBy(3_000)
        source.create(invitation("B"))
        advanceTimeBy(6_900)
        runCurrent()
        assertEquals(listOf(listOf("A")), slow.calls)

        // The check ends at 15 s; the change it did not see is debounced from there.
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf(listOf("A"), listOf("B")), slow.calls)
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
