// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.data.source.MoveUndo
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.timegrid.MoveRule
import com.qtekfun.ultimatecalendar.notify.SystemZone
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventMoveViewModelTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val start = Instant.parse("2026-10-05T07:00:00Z")
    private val account = CalendarAccount("me@example.com", "com.example")
    private val owner = calendar(1, CalendarAccess.OWNER)

    private lateinit var database: UltimateCalendarDatabase
    private lateinit var source: FakeCalendarSource
    private val created = mutableListOf<EventMoveViewModel>()

    private fun calendar(id: Long, access: CalendarAccess) = CalendarInfo(
        CalendarId(id),
        account,
        "Calendar $id",
        0xFF112233.toInt(),
        access,
        ownerEmail = "me@example.com"
    )

    private fun timed(minutes: Long) = EventTime.Timed(
        start.plusSeconds(minutes * 60),
        start.plusSeconds((minutes + 60) * 60),
        madrid
    )

    @BeforeEach
    fun open() {
        Dispatchers.setMain(StandardTestDispatcher())
        database = inMemoryDatabase()
        source = FakeCalendarSource(listOf(owner))
    }

    @AfterEach
    fun close() {
        created.forEach { it.viewModelScope.cancel() }
        database.close()
        // No resetMain: the cancelled work finishes on Main from another thread, after this.
    }

    private fun viewModel(): EventMoveViewModel {
        val repository =
            CalendarRepository(source, database.calendarSettingsDao(), Dispatchers.Unconfined)
        return EventMoveViewModel(source, repository, SystemZone { madrid }).also { created += it }
    }

    private suspend fun seed(rrule: String? = null): EventInstance {
        val id = requireNotNull(
            source.create(EventDraft(owner.id, "Review", timed(0), rrule = rrule)).getOrNull()
        )
        return source.instances(TimeRange(start.minusSeconds(60), start.plusSeconds(86_400L * 9)))
            .getOrNull().orEmpty().first { it.eventId == id }
    }

    private suspend fun timeOf(id: EventId) = source.event(id).getOrNull()?.time

    @Test
    fun `a saved move shows at once, tells the user and settles`() = runTest {
        val instance = seed()
        val viewModel = viewModel()

        viewModel.messages.test {
            viewModel.move(instance, timed(90), null)
            assertEquals(PendingMove(instance, timed(90)), viewModel.pending.value)

            val message = awaitItem() as MoveMessage.Moved
            assertEquals(timed(90), timeOf(instance.eventId))
            assertEquals(PendingMove(instance, timed(90)), message.move)
            assertInstanceOf(MoveUndo.Restore::class.java, message.undo)
            // The new position is kept for a moment, until the new data has been read.
            assertEquals(PendingMove(instance, timed(90)), viewModel.pending.value)
            advanceTimeBy(EventMoveViewModel.SETTLE_MS + 1)
            assertNull(viewModel.pending.value)
        }
    }

    @Test
    fun `a move that is undone puts the old time back`() = runTest {
        val instance = seed()
        val viewModel = viewModel()

        viewModel.messages.test {
            viewModel.move(instance, timed(90), null)
            val message = awaitItem() as MoveMessage.Moved

            viewModel.undo(message.undo)
            advanceUntilIdle()

            assertEquals(timed(0), timeOf(instance.eventId))
            expectNoEvents()
        }
    }

    @Test
    fun `a refused move goes back to where the event was and says why`() = runTest {
        val instance = seed()
        source.addCalendar(calendar(1, CalendarAccess.READ))
        val viewModel = viewModel()

        viewModel.messages.test {
            viewModel.move(instance, timed(90), null)

            assertEquals(MoveMessage.Failed(CalendarError.ReadOnly), awaitItem())
            assertNull(viewModel.pending.value)
            assertEquals(timed(0), timeOf(instance.eventId))
        }
    }

    @Test
    fun `an undo that is refused is told`() = runTest {
        val instance = seed()
        val viewModel = viewModel()

        viewModel.messages.test {
            viewModel.move(instance, timed(90), null)
            val message = awaitItem() as MoveMessage.Moved
            source.addCalendar(calendar(1, CalendarAccess.READ))

            viewModel.undo(message.undo)

            assertEquals(MoveMessage.UndoFailed(CalendarError.ReadOnly), awaitItem())
        }
    }

    @Test
    fun `nothing else is saved while a change is under way`() = runTest {
        val instance = seed()
        val viewModel = viewModel()

        viewModel.messages.test {
            viewModel.move(instance, timed(90), null)
            viewModel.move(instance, timed(180), null)
            awaitItem()

            assertEquals(timed(90), timeOf(instance.eventId))
            expectNoEvents()
        }
    }

    @Test
    fun `a repeating event is moved for the occurrences chosen`() = runTest {
        val instance = seed("FREQ=DAILY;COUNT=3")
        val viewModel = viewModel()

        viewModel.messages.test {
            viewModel.move(instance, timed(30), RecurrenceScope.ALL)
            awaitItem()

            assertEquals(timed(30), timeOf(instance.eventId))
        }
    }

    @Test
    fun `a repeating event cannot be moved without choosing the occurrences`() = runTest {
        val instance = seed("FREQ=DAILY;COUNT=3")
        val viewModel = viewModel()

        viewModel.messages.test {
            viewModel.move(instance, timed(30), null)

            val failed = awaitItem() as MoveMessage.Failed
            assertInstanceOf(CalendarError.Invalid::class.java, failed.error)
            assertNull(viewModel.pending.value)
        }
    }

    @Test
    fun `the rules follow the access of each calendar`() = runTest {
        source.addCalendar(calendar(2, CalendarAccess.READ))
        source.addCalendar(
            CalendarInfo(
                CalendarId(3),
                CalendarAccount("local", "LOCAL"),
                "Local",
                0,
                CalendarAccess.OWNER
            )
        )
        val viewModel = viewModel()

        viewModel.rules.test {
            var rules = awaitItem()
            while (rules.size < 3) rules = awaitItem()
            assertEquals(MoveRule(canEdit = true, canMoveOne = true), rules[CalendarId(1)])
            assertEquals(MoveRule(canEdit = false, canMoveOne = true), rules[CalendarId(2)])
            assertEquals(MoveRule(canEdit = true, canMoveOne = false), rules[CalendarId(3)])
            cancelAndIgnoreRemainingEvents()
        }
    }
}
