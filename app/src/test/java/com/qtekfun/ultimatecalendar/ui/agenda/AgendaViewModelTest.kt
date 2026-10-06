// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.agenda

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaItem
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.CalendarSettings
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.SystemZone
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModelTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val clock = Clock.fixed(Instant.parse("2026-10-06T09:30:00Z"), ZoneId.of("UTC"))
    private val today = LocalDate.parse("2026-10-06")
    private val account = CalendarAccount("me@example.com", "com.google")
    private val work = calendar(1, "Work", 0xFF112233.toInt())
    private val home = calendar(2, "Home", 0xFF445566.toInt())

    private lateinit var database: UltimateCalendarDatabase
    private lateinit var source: FakeCalendarSource
    private lateinit var repository: CalendarRepository
    private val main = UnconfinedTestDispatcher()

    private fun calendar(id: Long, name: String, color: Int) = CalendarInfo(
        CalendarId(id),
        account,
        name,
        color,
        CalendarAccess.OWNER,
        ownerEmail = "me@example.com"
    )

    @BeforeEach
    fun open() {
        Dispatchers.setMain(main)
        database = inMemoryDatabase()
        source = FakeCalendarSource(listOf(work, home))
        repository =
            CalendarRepository(source, database.calendarSettingsDao(), Dispatchers.Unconfined)
    }

    @AfterEach
    fun close() {
        database.close()
    }

    private fun viewModel(repository: CalendarRepository = this.repository) =
        AgendaViewModel(repository, clock, SystemZone { madrid }, Dispatchers.Unconfined)

    private suspend fun add(calendar: CalendarInfo, title: String, day: LocalDate, hour: Int = 9) =
        source.create(
            EventDraft(
                calendarId = calendar.id,
                title = title,
                time = EventTime.Timed(
                    day.atTime(hour, 0).atZone(madrid).toInstant(),
                    day.atTime(hour + 1, 0).atZone(madrid).toInstant(),
                    madrid
                )
            )
        )

    /** The next state that satisfies [accept]: the ones in between are loading or stale. */
    private suspend fun ReceiveTurbine<AgendaState>.awaitState(
        accept: (AgendaState) -> Boolean = { it.status != AgendaStatus.LOADING }
    ): AgendaState {
        while (true) {
            val next = awaitItem()
            if (accept(next)) return next
        }
    }

    private fun AgendaState.titles() =
        items.filterIsInstance<AgendaItem.EventRow>().map { it.entry.instance.title }

    @Test
    fun `the first state is loading, around today`() = runTest {
        val first = viewModel().state.value

        assertEquals(AgendaStatus.LOADING, first.status)
        assertEquals(today, first.anchor)
        assertTrue(first.items.isEmpty())
    }

    @Test
    fun `it reads the window and lists the events by day with their calendar's color`() = runTest {
        add(work, "Later", today.plusDays(2))
        add(home, "Sooner", today)
        add(work, "Far away", today.plusDays(200))

        viewModel().state.test {
            val ready = awaitState()

            assertEquals(AgendaStatus.READY, ready.status)
            assertEquals(listOf("Sooner", "Later"), ready.titles())
            val colors = ready.items.filterIsInstance<AgendaItem.EventRow>().map { it.entry.color }
            assertEquals(listOf(0xFF445566.toInt(), 0xFF112233.toInt()), colors)
            assertEquals(madrid, ready.zone)
        }
    }

    @Test
    fun `showing another date reloads around it and starts a new generation`() = runTest {
        add(work, "Near", today)
        add(work, "Next year", today.plusDays(300))
        val model = viewModel()

        model.state.test {
            val before = awaitState()
            model.show(today.plusDays(300))
            val after = awaitState {
                it.anchor == today.plusDays(300) &&
                    it.status != AgendaStatus.LOADING
            }

            assertEquals(listOf("Near"), before.titles())
            assertEquals(listOf("Next year"), after.titles())
            assertEquals(today.plusDays(300), after.anchor)
            assertNotEquals(before.generation, after.generation)
        }
    }

    @Test
    fun `scrolling later reads further and keeps the generation`() = runTest {
        add(work, "Soon", today)
        add(work, "In two months", today.plusDays(55))
        val model = viewModel()

        model.state.test {
            val before = awaitState()
            model.later()
            val after = awaitState { it.titles().size == 2 }

            assertEquals(listOf("Soon"), before.titles())
            assertEquals(listOf("Soon", "In two months"), after.titles())
            assertEquals(before.generation, after.generation)
            assertTrue(after.range.endExclusive.isAfter(before.range.endExclusive))
        }
    }

    @Test
    fun `scrolling earlier reads further back`() = runTest {
        add(work, "Long ago", today.minusDays(40))
        add(work, "Soon", today)
        val model = viewModel()

        model.state.test {
            val before = awaitState()
            model.earlier()
            val after = awaitState { it.titles().size == 2 }

            assertEquals(listOf("Soon"), before.titles())
            assertEquals(listOf("Long ago", "Soon"), after.titles())
        }
    }

    @Test
    fun `with nothing in the window it keeps looking ahead until it finds an event`() = runTest {
        add(work, "Eventually", today.plusDays(150))

        viewModel().state.test {
            val ready = awaitState()

            assertEquals(AgendaStatus.READY, ready.status)
            assertEquals(listOf("Eventually"), ready.titles())
        }
    }

    @Test
    fun `with nothing within a year it is ready and empty`() = runTest {
        viewModel().state.test {
            val ready = awaitState()

            assertEquals(AgendaStatus.READY, ready.status)
            assertTrue(ready.items.isEmpty())
            assertEquals(today.plusDays(366), ready.range.endExclusive)
        }
    }

    @Test
    fun `events of a hidden calendar are not listed`() = runTest {
        add(work, "Shown", today)
        add(home, "Hidden", today)
        repository.saveSettings(home.id, CalendarSettings(visible = false))

        viewModel().state.test {
            assertEquals(listOf("Shown"), awaitState().titles())
        }
    }

    @Test
    fun `the list follows changes in the source`() = runTest {
        add(work, "First", today)
        val model = viewModel()

        model.state.test {
            assertEquals(listOf("First"), awaitState().titles())
            add(work, "Added", today.plusDays(1))
            assertEquals(listOf("First", "Added"), awaitState { it.titles().size == 2 }.titles())
        }
    }

    @Test
    fun `a source that fails gives a failed state with no rows`() = runTest {
        val failing = mockk<CalendarSource>()
        every { failing.changes } returns emptyFlow()
        coEvery { failing.calendars() } returns
            CalendarResult.Failure(CalendarError.PermissionDenied)
        val model = viewModel(
            CalendarRepository(failing, database.calendarSettingsDao(), Dispatchers.Unconfined)
        )

        model.state.test {
            val failed = awaitState()

            assertEquals(AgendaStatus.FAILED, failed.status)
            assertTrue(failed.items.isEmpty())
        }
    }
}
