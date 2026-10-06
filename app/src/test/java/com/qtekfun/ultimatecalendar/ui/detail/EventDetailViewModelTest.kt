// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.settings.FakePreferences
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.SystemZone
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val start = Instant.parse("2026-10-05T07:00:00Z")
    private val account = CalendarAccount("me@example.com", "com.example")
    private val work = calendar(1, CalendarAccess.OWNER)
    private val readOnly = calendar(2, CalendarAccess.READ)

    private lateinit var database: UltimateCalendarDatabase
    private lateinit var source: FakeCalendarSource
    private lateinit var settings: SettingsRepository
    private lateinit var repository: CalendarRepository

    private fun calendar(id: Long, access: CalendarAccess) = CalendarInfo(
        CalendarId(id),
        account,
        "Calendar $id",
        0xFF112233.toInt(),
        access,
        ownerEmail = "me@example.com"
    )

    @BeforeEach
    fun open() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = inMemoryDatabase()
        source = FakeCalendarSource(listOf(work, readOnly))
        settings = SettingsRepository(FakePreferences(), FakePreferences())
        repository =
            CalendarRepository(source, database.calendarSettingsDao(), Dispatchers.Unconfined)
    }

    @AfterEach
    fun close() {
        created.forEach { it.viewModelScope.cancel() }
        database.close()
    }

    private val created = mutableListOf<EventDetailViewModel>()

    private fun viewModel(on: CalendarSource = source) =
        EventDetailViewModel(on, repository, settings, SystemZone { madrid }).also { created += it }

    /** Waits (in real time: Room answers on its own threads) for a state that [matches]. */
    private suspend fun EventDetailViewModel.awaitState(
        matches: (DetailState) -> Boolean
    ): DetailState {
        var found: DetailState = DetailState.Loading
        state.test {
            found = awaitItem()
            while (!matches(found)) found = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return found
    }

    private suspend fun EventDetailViewModel.show(ref: EventRef) {
        open(ref)
        awaitState { it !is DetailState.Loading }
    }

    private fun draft(
        calendar: CalendarInfo = work,
        rrule: String? = null,
        attendees: List<Attendee> = emptyList()
    ) = EventDraft(
        calendar.id,
        "Design review",
        EventTime.Timed(start, start.plusSeconds(3600), madrid),
        rrule = rrule,
        attendees = attendees
    )

    private val invited = listOf(
        Attendee.of("boss@example.com", status = AttendeeStatus.ACCEPTED),
        Attendee.of("me@example.com", status = AttendeeStatus.NEEDS_ACTION)
    )

    private suspend fun create(draft: EventDraft): EventRef {
        val id = requireNotNull(source.create(draft).getOrNull())
        val instance = source.instances(
            TimeRange(start.minusSeconds(60), start.plusSeconds(86_400L * 30))
        )
            .getOrNull().orEmpty().first { it.eventId == id }
        return EventRef.of(instance)
    }

    private fun EventDetailViewModel.loaded() = state.value as DetailState.Loaded

    @Test
    fun `opening an event loads it with what the user may do`() = runTest {
        val ref = create(draft(attendees = invited))
        val viewModel = viewModel()

        viewModel.state.test {
            assertEquals(DetailState.Loading, awaitItem())
            viewModel.open(ref)
            val loaded = awaitItem() as DetailState.Loaded
            assertEquals("Design review", loaded.detail.title)
            assertTrue(loaded.detail.canRespond)
            assertTrue(loaded.detail.canEdit)
            assertEquals("Calendar 1", loaded.detail.calendar?.displayName)
            assertEquals(2, loaded.detail.attendees?.total)
        }
    }

    @Test
    fun `an event that does not exist fails and can be retried`() = runTest {
        val viewModel = viewModel()
        viewModel.show(EventRef(EventId(99), 0, 0, allDay = false))
        assertEquals(DetailState.Failed(CalendarError.NotFound), viewModel.state.value)

        val ref = create(draft())
        viewModel.show(ref.copy(eventId = EventId(99)))
        viewModel.retry()
        viewModel.awaitState { it is DetailState.Failed }
        assertTrue(viewModel.state.value is DetailState.Failed)
    }

    @Test
    fun `an alias of the user is enough to be an invitee`() = runTest {
        settings.update { it.copy(ownEmails = listOf("work@other.org")) }
        val ref = create(draft(attendees = listOf(Attendee.of("work@other.org"))))
        val viewModel = viewModel()
        viewModel.show(ref)
        assertTrue(viewModel.loaded().detail.canRespond)
    }

    @Test
    fun `an answer shows at once, then is kept when the source takes it`() = runTest {
        val ref = create(draft(attendees = invited))
        val gate = CompletableDeferred<Unit>()
        val slow = object : CalendarSource by source {
            override suspend fun respond(
                id: EventId,
                status: AttendeeStatus
            ): CalendarResult<Unit> {
                gate.await()
                return source.respond(id, status)
            }
        }
        val viewModel = viewModel(slow)
        viewModel.show(ref)

        viewModel.respond(AttendeeStatus.ACCEPTED)

        val sending = viewModel.loaded()
        assertEquals(AttendeeStatus.ACCEPTED, sending.responding)
        assertEquals(AttendeeStatus.ACCEPTED, sending.detail.self?.status)
        viewModel.respond(AttendeeStatus.DECLINED)
        assertEquals(AttendeeStatus.ACCEPTED, viewModel.loaded().detail.self?.status)

        gate.complete(Unit)

        viewModel.awaitState { (it as? DetailState.Loaded)?.responding == null }
        val done = viewModel.loaded()
        assertEquals(null, done.responding)
        assertEquals(AttendeeStatus.ACCEPTED, done.detail.self?.status)
        val stored = source.event(ref.eventId).getOrNull()
        assertEquals(AttendeeStatus.ACCEPTED, stored?.attendees?.last()?.status)
    }

    @Test
    fun `a refused answer goes back and says why`() = runTest {
        val ref = create(draft(attendees = invited))
        val gate = CompletableDeferred<Unit>()
        val refusing = object : CalendarSource by source {
            override suspend fun respond(
                id: EventId,
                status: AttendeeStatus
            ): CalendarResult<Unit> {
                gate.await()
                return CalendarResult.Failure(CalendarError.SourceFailure("offline"))
            }
        }
        val viewModel = viewModel(refusing)
        viewModel.show(ref)

        viewModel.failure.test {
            viewModel.respond(AttendeeStatus.TENTATIVE)
            assertEquals(AttendeeStatus.TENTATIVE, viewModel.loaded().detail.self?.status)

            gate.complete(Unit)

            assertEquals(
                DetailFailure(DetailAction.RESPOND, CalendarError.SourceFailure("offline")),
                awaitItem()
            )
            val back = viewModel.loaded()
            assertEquals(AttendeeStatus.NEEDS_ACTION, back.detail.self?.status)
            assertEquals(null, back.responding)
            assertEquals(
                AttendeeStatus.NEEDS_ACTION,
                source.event(ref.eventId).getOrNull()?.attendees?.last()?.status
            )
        }
    }

    @Test
    fun `answering is ignored when it cannot or need not be done`() = runTest {
        val notInvited = create(draft(attendees = listOf(Attendee.of("boss@example.com"))))
        val viewModel = viewModel()
        viewModel.show(notInvited)
        viewModel.respond(AttendeeStatus.ACCEPTED)
        assertEquals(null, viewModel.loaded().responding)
        assertEquals(null, viewModel.loaded().detail.self)

        val mock = mockk<CalendarSource>(relaxed = true)
        every { mock.changes } returns emptyFlow()
        val answered = Event(
            EventId(1),
            work.id,
            "Done",
            EventTime.Timed(start, start.plusSeconds(60), madrid),
            attendees = listOf(Attendee.of("me@example.com", status = AttendeeStatus.ACCEPTED))
        )
        coEvery { mock.event(EventId(1)) } returns CalendarResult.Success(answered)
        val again = viewModel(mock)
        again.show(EventRef(EventId(1), 0, 0, false))
        again.respond(AttendeeStatus.ACCEPTED)
        coVerify(exactly = 0) { mock.respond(any(), any()) }
    }

    @Test
    fun `before anything is loaded nothing can be answered or deleted`() = runTest {
        val viewModel = viewModel()
        viewModel.respond(AttendeeStatus.ACCEPTED)
        viewModel.delete(RecurrenceScope.ALL)
        viewModel.undoDelete()
        assertEquals(DetailState.Loading, viewModel.state.value)
    }

    @Test
    fun `a calendar that cannot be edited cannot delete`() = runTest {
        source.addCalendar(calendar(3, CalendarAccess.CONTRIBUTE))
        val ref = create(draft(calendar(3, CalendarAccess.CONTRIBUTE), attendees = invited))
        val viewModel = viewModel()
        viewModel.show(ref)
        assertFalse(viewModel.loaded().detail.canEdit)
        assertTrue(viewModel.loaded().detail.canRespond)

        viewModel.delete(RecurrenceScope.ALL)

        assertTrue(viewModel.state.value is DetailState.Loaded)
        assertTrue(source.event(ref.eventId) is CalendarResult.Success)
    }

    @Test
    fun `a lone event is deleted and can be put back when nobody was invited`() = runTest {
        val ref = create(draft())
        val viewModel = viewModel()
        viewModel.show(ref)

        viewModel.delete(RecurrenceScope.THIS)

        assertEquals(DetailState.Deleted(canUndo = true), viewModel.state.value)
        assertEquals(CalendarResult.Failure(CalendarError.NotFound), source.event(ref.eventId))

        viewModel.undoDelete()

        viewModel.awaitState { it is DetailState.Loaded }
        val back = viewModel.loaded()
        assertEquals("Design review", back.detail.title)
        assertTrue(source.event(back.detail.event.id) is CalendarResult.Success)
    }

    @Test
    fun `an event with invitees is deleted without undo`() = runTest {
        val ref = create(draft(attendees = invited))
        val viewModel = viewModel()
        viewModel.show(ref)

        viewModel.delete(RecurrenceScope.ALL)

        assertEquals(DetailState.Deleted(canUndo = false), viewModel.state.value)
        viewModel.undoDelete()
        assertEquals(DetailState.Deleted(canUndo = false), viewModel.state.value)
    }

    private suspend fun series(): EventRef {
        val ref = create(draft(rrule = "FREQ=DAILY;COUNT=4"))
        val third = source.instances(TimeRange(start, start.plusSeconds(86_400L * 10)))
            .getOrNull().orEmpty()[2]
        return EventRef.of(third).copy(eventId = ref.eventId)
    }

    private suspend fun remaining() =
        source.instances(TimeRange(start, start.plusSeconds(86_400L * 10)))
            .getOrNull().orEmpty().size

    @Test
    fun `deleting only this occurrence of a series keeps the rest`() = runTest {
        val ref = series()
        val viewModel = viewModel()
        viewModel.show(ref)
        assertTrue(viewModel.loaded().detail.isSeries)

        viewModel.delete(RecurrenceScope.THIS)

        assertEquals(DetailState.Deleted(canUndo = false), viewModel.state.value)
        assertEquals(3, remaining())
    }

    @Test
    fun `deleting all of a series removes it`() = runTest {
        val ref = series()
        val viewModel = viewModel()
        viewModel.show(ref)

        viewModel.delete(RecurrenceScope.ALL)

        assertEquals(DetailState.Deleted(canUndo = true), viewModel.state.value)
        assertEquals(0, remaining())
    }

    @Test
    fun `deleting this and the following ends the series and can be undone`() = runTest {
        val master = draft(rrule = "FREQ=DAILY;COUNT=4").toEvent(EventId(7), "me@example.com")
        val mock = mockk<CalendarSource>()
        every { mock.changes } returns emptyFlow()
        coEvery { mock.event(EventId(7)) } returns CalendarResult.Success(master)
        coEvery { mock.calendars() } returns CalendarResult.Success(listOf(work))
        coEvery { mock.update(any()) } returns CalendarResult.Success(Unit)
        val viewModel = viewModel(mock)
        val third = master.time.startIn(madrid).plusSeconds(2 * 86_400)
        viewModel.show(
            EventRef(EventId(7), third.toEpochMilli(), third.toEpochMilli() + 3_600_000, false)
        )

        viewModel.delete(RecurrenceScope.THIS_AND_FOLLOWING)

        assertEquals(DetailState.Deleted(canUndo = true), viewModel.state.value)
        coVerify(exactly = 1) { mock.update(match { it.rrule?.contains("UNTIL") == true }) }

        viewModel.undoDelete()

        viewModel.awaitState { it is DetailState.Loaded }
        coVerify(exactly = 1) { mock.update(master) }
    }

    @Test
    fun `a deletion the source refuses leaves the event as it was and says so`() = runTest {
        val ref = create(draft())
        val refusing = object : CalendarSource by source {
            override suspend fun delete(id: EventId): CalendarResult<Unit> =
                CalendarResult.Failure(CalendarError.PermissionDenied)
        }
        val viewModel = viewModel(refusing)
        viewModel.show(ref)
        val before = viewModel.loaded()

        viewModel.failure.test {
            viewModel.delete(RecurrenceScope.ALL)
            assertEquals(
                DetailFailure(DetailAction.DELETE, CalendarError.PermissionDenied),
                awaitItem()
            )
        }
        assertEquals(before, viewModel.state.value)
    }

    @Test
    fun `a series the app cannot split reports the failure`() = runTest {
        val ref = series()
        val viewModel = viewModel()
        viewModel.show(ref)

        viewModel.failure.test {
            // The fake only keeps rules with a count, so cutting the series with an UNTIL fails.
            viewModel.delete(RecurrenceScope.THIS_AND_FOLLOWING)
            assertEquals(DetailAction.DELETE, awaitItem().action)
        }
        assertTrue(viewModel.state.value is DetailState.Loaded)
        assertEquals(4, remaining())
    }

    @Test
    fun `an undo the source refuses says so and gives up`() = runTest {
        val ref = create(draft())
        val noCreate = object : CalendarSource by source {
            override suspend fun create(draft: EventDraft): CalendarResult<EventId> =
                CalendarResult.Failure(CalendarError.SourceFailure("down"))
        }
        val viewModel = viewModel(noCreate)
        viewModel.show(ref)
        viewModel.delete(RecurrenceScope.ALL)

        viewModel.failure.test {
            viewModel.undoDelete()
            assertEquals(DetailAction.UNDO, awaitItem().action)
        }
        assertEquals(DetailState.Deleted(canUndo = false), viewModel.state.value)
    }

    @Test
    fun `changes in the source refresh what is shown`() = runTest {
        val ref = create(draft())
        val viewModel = viewModel()
        viewModel.show(ref)

        val stored = requireNotNull(source.event(ref.eventId).getOrNull())
        source.update(stored.copy(title = "Renamed"))

        viewModel.awaitState { (it as? DetailState.Loaded)?.detail?.title == "Renamed" }

        source.delete(ref.eventId)

        viewModel.awaitState { it is DetailState.Failed }
        assertEquals(DetailState.Failed(CalendarError.NotFound), viewModel.state.value)
    }

    @Test
    fun `opening the same occurrence again does not start over`() = runTest {
        val ref = create(draft())
        val viewModel = viewModel()
        viewModel.show(ref)
        val first = viewModel.loaded()

        viewModel.show(ref)

        assertEquals(first, viewModel.state.value)
        assertInstanceOf(DetailState.Loaded::class.java, viewModel.state.value)
    }
}
