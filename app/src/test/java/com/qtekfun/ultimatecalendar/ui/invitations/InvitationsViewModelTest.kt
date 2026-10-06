// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.invitations

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.invitations.InvitationInbox
import com.qtekfun.ultimatecalendar.data.invitations.InvitationResponses
import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.invitations.ResponseOutcome
import com.qtekfun.ultimatecalendar.data.invitations.SourceInvitationResponses
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAnswer
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationChanges
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.RecordingSurface
import com.qtekfun.ultimatecalendar.notify.SystemZone
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.calendar
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.invitation
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.now
import com.qtekfun.ultimatecalendar.sync.FixedCheckSettings
import com.qtekfun.ultimatecalendar.sync.InMemoryNotifiedDao
import com.qtekfun.ultimatecalendar.sync.InvitationCheckCoordinator
import com.qtekfun.ultimatecalendar.sync.InvitationCheckOutcome
import com.qtekfun.ultimatecalendar.sync.InvitationChecker
import com.qtekfun.ultimatecalendar.sync.MutableClock
import com.qtekfun.ultimatecalendar.sync.RecordingNotifier
import com.qtekfun.ultimatecalendar.sync.RecordingSyncRequester
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InvitationsViewModelTest {
    private val clock = MutableClock(now)
    private val source = FakeCalendarSource(listOf(calendar(1), calendar(2)))
    private val surface = RecordingSurface()
    private val coordinator = mockk<InvitationCheckCoordinator>()
    private val main = UnconfinedTestDispatcher()
    private var zone: ZoneId = ZoneId.of("Europe/Madrid")
    private var responses: InvitationResponses = SourceInvitationResponses(source)
    private val inbox = InvitationInbox(
        InvitationChecker(
            source,
            RecordingSyncRequester(),
            NotifiedInvitations(InMemoryNotifiedDao(), Dispatchers.Unconfined),
            RecordingNotifier(),
            FixedCheckSettings(),
            clock,
            Dispatchers.Unconfined
        )
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(main)
        coEvery { coordinator.checkNow() } returns
            InvitationCheckOutcome.Done(
                InvitationChanges(emptyList(), emptyList(), emptyList(), emptyList()),
                0
            )
    }

    @AfterEach
    fun tearDown() {
        // Let every WhileSubscribed upstream stop before the next test.
        main.scheduler.advanceTimeBy(STOP_TIMEOUT_MS)
    }

    private fun viewModel() = InvitationsViewModel(
        inbox,
        { key, status -> responses.respond(key, status) },
        coordinator,
        surface,
        SystemZone { zone }
    )

    private suspend fun create(title: String, calendar: Long = 1, hours: Long = 1): Invitation {
        val draft = invitation(title, calendar = calendar, start = now.plusSeconds(hours * 3600))
        val id = (source.create(draft) as CalendarResult.Success).value
        return Invitation(InvitationKey(CalendarId(calendar), id), title, draft.time)
    }

    /** The first state after loading; with an unconfined dispatcher "loading" may be skipped. */
    private suspend fun ReceiveTurbine<InvitationsUiState>.awaitLoaded(): InvitationsUiState {
        var state = awaitItem()
        while (state.loading) state = awaitItem()
        return state
    }

    private suspend fun InvitationsViewModel.loaded() = state.first { !it.loading }

    private suspend fun InvitationsViewModel.titles() =
        loaded().days.flatMap { day -> day.invitations.map { it.title } }

    private suspend fun statusOf(id: EventId) =
        (source.event(id) as CalendarResult.Success).value.attendees.single().status

    @Test
    fun `it starts loading and then lists every calendar by day, soonest first`() = runTest {
        create("Dinner", calendar = 2, hours = 30)
        create("Later lunch", calendar = 1, hours = 3)
        create("Early lunch", calendar = 2, hours = 1)

        viewModel().state.test {
            val loaded = awaitLoaded()
            assertFalse(loaded.loading)
            assertEquals(
                listOf("Early lunch", "Later lunch", "Dinner"),
                loaded.days.flatMap { day -> day.invitations.map { it.title } }
            )
            assertEquals(
                listOf(LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-11")),
                loaded.days.map { it.date }
            )
        }
    }

    @Test
    fun `an empty inbox is loaded and empty, not loading`() = runTest {
        val state = viewModel().loaded()
        assertTrue(state.days.isEmpty())
        assertFalse(state.refreshing)
    }

    @Test
    fun `the day depends on the phone's zone`() = runTest {
        // 12:00 UTC + 12 h = 00:00 UTC on the 11th: still the 10th in New York, the 11th in Madrid.
        create("Midnight", hours = 12)
        assertEquals(
            LocalDate.parse("2026-06-11"),
            viewModel().loaded().days.single().date
        )
        zone = ZoneId.of("America/New_York")
        assertEquals(
            LocalDate.parse("2026-06-10"),
            viewModel().loaded().days.single().date
        )
    }

    @Test
    fun `answering hides it at once, stores it, clears the notification, offers undo`() = runTest {
        val lunch = create("Lunch")
        create("Dinner", hours = 5)
        val gate = CompletableDeferred<Unit>()
        val slow = SourceInvitationResponses(source)
        responses = InvitationResponses { key, status ->
            gate.await()
            slow.respond(key, status)
        }
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.state.test {
                assertEquals(2, awaitLoaded().days.sumOf { it.invitations.size })

                viewModel.answer(lunch, InvitationAnswer.ACCEPT)
                // Hidden before the source answered.
                assertEquals(
                    listOf("Dinner"),
                    awaitItem().days.flatMap { d ->
                        d.invitations.map { it.title }
                    }
                )
                assertEquals(AttendeeStatus.NEEDS_ACTION, statusOf(lunch.key.eventId))

                gate.complete(Unit)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(InvitationsEvent.Answered(lunch, InvitationAnswer.ACCEPT), awaitItem())
        }
        assertEquals(AttendeeStatus.ACCEPTED, statusOf(lunch.key.eventId))
        assertEquals(listOf("cancel ${lunch.key.eventId.value}", "summary"), surface.calls)
    }

    @Test
    fun `each answer writes its own status`() = runTest {
        val a = create("A")
        val b = create("B", hours = 2)
        val c = create("C", hours = 3)
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.answer(a, InvitationAnswer.ACCEPT)
        viewModel.answer(b, InvitationAnswer.MAYBE)
        viewModel.answer(c, InvitationAnswer.DECLINE)
        assertEquals(AttendeeStatus.ACCEPTED, statusOf(a.key.eventId))
        assertEquals(AttendeeStatus.TENTATIVE, statusOf(b.key.eventId))
        assertEquals(AttendeeStatus.DECLINED, statusOf(c.key.eventId))
    }

    @Test
    fun `a failed answer brings the invitation back with a message`() = runTest {
        val lunch = create("Lunch")
        val gate = CompletableDeferred<Unit>()
        responses = InvitationResponses { _, _ ->
            gate.await()
            ResponseOutcome.Failed(CalendarError.SourceFailure("down"))
        }
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.state.test {
                assertEquals(
                    listOf("Lunch"),
                    awaitLoaded().days.flatMap { d ->
                        d.invitations.map { it.title }
                    }
                )
                viewModel.answer(lunch, InvitationAnswer.DECLINE)
                assertTrue(awaitItem().days.isEmpty())

                gate.complete(Unit)
                assertEquals(
                    listOf("Lunch"),
                    awaitItem().days.flatMap { d ->
                        d.invitations.map { it.title }
                    }
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(InvitationsEvent.AnswerFailed, awaitItem())
        }
        assertEquals(AttendeeStatus.NEEDS_ACTION, statusOf(lunch.key.eventId))
        assertTrue(surface.calls.isEmpty())
    }

    @Test
    fun `an event deleted meanwhile stays out and says so`() = runTest {
        val lunch = create("Lunch")
        val viewModel = viewModel()
        viewModel.loaded()
        source.delete(lunch.key.eventId)
        viewModel.events.test {
            viewModel.answer(lunch, InvitationAnswer.ACCEPT)
            assertEquals(InvitationsEvent.Gone, awaitItem())
        }
        assertEquals(listOf("cancel ${lunch.key.eventId.value}", "summary"), surface.calls)
        assertTrue(viewModel.titles().isEmpty())
    }

    @Test
    fun `undo makes the invitation pending again and shows it`() = runTest {
        val lunch = create("Lunch")
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.answer(lunch, InvitationAnswer.ACCEPT)
        advanceUntilIdle()
        assertTrue(viewModel.titles().isEmpty())

        viewModel.undo(lunch)
        advanceUntilIdle()

        assertEquals(AttendeeStatus.NEEDS_ACTION, statusOf(lunch.key.eventId))
        assertEquals(listOf("Lunch"), viewModel.titles())
    }

    @Test
    fun `undo that cannot be stored says so and the answer stays`() = runTest {
        val lunch = create("Lunch")
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.answer(lunch, InvitationAnswer.ACCEPT)
        responses =
            InvitationResponses { _, _ -> ResponseOutcome.Failed(CalendarError.PermissionDenied) }
        viewModel.events.test {
            assertEquals(InvitationsEvent.Answered(lunch, InvitationAnswer.ACCEPT), awaitItem())
            viewModel.undo(lunch)
            assertEquals(InvitationsEvent.AnswerFailed, awaitItem())
        }
        assertEquals(AttendeeStatus.ACCEPTED, statusOf(lunch.key.eventId))
        assertTrue(viewModel.titles().isEmpty())
    }

    @Test
    fun `undo of an event that was deleted says so`() = runTest {
        val lunch = create("Lunch")
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.answer(lunch, InvitationAnswer.ACCEPT)
        source.delete(lunch.key.eventId)
        viewModel.events.test {
            assertEquals(InvitationsEvent.Answered(lunch, InvitationAnswer.ACCEPT), awaitItem())
            viewModel.undo(lunch)
            assertEquals(InvitationsEvent.Gone, awaitItem())
        }
    }

    @Test
    fun `pull to refresh checks now, shows the spinner meanwhile and picks up what is new`() =
        runTest {
            val gate = CompletableDeferred<InvitationCheckOutcome>()
            coEvery { coordinator.checkNow() } coAnswers { gate.await() }
            val viewModel = viewModel()

            viewModel.state.test {
                assertTrue(awaitLoaded().days.isEmpty())
                val fresh = create("Fresh")

                viewModel.refresh()
                assertTrue(awaitItem().refreshing)
                // A second pull while one runs does nothing.
                viewModel.refresh()

                gate.complete(
                    InvitationCheckOutcome.Done(
                        InvitationChanges(emptyList(), emptyList(), emptyList(), emptyList()),
                        1
                    )
                )
                var last = awaitItem()
                while (last.refreshing || last.days.isEmpty()) last = awaitItem()
                assertEquals(
                    listOf(fresh.title),
                    last.days.flatMap { d -> d.invitations.map { it.title } }
                )
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 1) { coordinator.checkNow() }
        }

    @Test
    fun `a refresh that fails tells the user and stops the spinner`() = runTest {
        coEvery { coordinator.checkNow() } returns
            InvitationCheckOutcome.Failed(CalendarError.PermissionDenied)
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.events.test {
            viewModel.refresh()
            assertEquals(InvitationsEvent.RefreshFailed, awaitItem())
        }
        assertFalse(viewModel.loaded().refreshing)
        // It can be pulled again.
        viewModel.refresh()
        coVerify(exactly = 2) { coordinator.checkNow() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
