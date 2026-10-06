// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.CalendarSettings
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimeGridViewModelTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val clock = Clock.fixed(Instant.parse("2026-03-11T09:30:30Z"), ZoneId.of("UTC"))
    private val day = LocalDate.parse("2026-03-11")
    private val range = DateRange(day, day.plusDays(1))
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
        TimeGridViewModel(repository, clock, SystemZone { madrid })

    private suspend fun add(calendar: CalendarInfo, title: String, hour: Int, color: Int? = null) =
        source.create(
            EventDraft(
                calendarId = calendar.id,
                title = title,
                time = EventTime.Timed(
                    day.atTime(hour, 0).atZone(madrid).toInstant(),
                    day.atTime(hour + 1, 0).atZone(madrid).toInstant(),
                    madrid
                ),
                color = color
            )
        )

    @Test
    fun `a page lists the events of the range laid out in the grid`() = runTest {
        add(work, "Standup", 9)

        val state = viewModel().page(range).first()

        assertFalse(state.failed)
        val block = state.page.timed.single()
        assertEquals("Standup", block.instance.title)
        assertEquals(9 * 60, block.startMinute)
    }

    @Test
    fun `an event takes its calendar's color unless it has its own`() = runTest {
        add(work, "Plain", 8)
        add(home, "Painted", 10, color = 0xFF778899.toInt())
        add(home, "Inherits", 12)

        val state = viewModel().page(range).first()

        assertEquals(
            mapOf(
                "Plain" to 0xFF112233.toInt(),
                "Painted" to 0xFF778899.toInt(),
                "Inherits" to 0xFF445566.toInt()
            ),
            state.page.timed.associate { it.instance.title to it.color }
        )
    }

    @Test
    fun `a local calendar color override reaches the events`() = runTest {
        add(work, "Plain", 8)
        repository.saveSettings(work.id, CalendarSettings(color = 0xFF0000FF.toInt()))

        val state = viewModel().page(range).first()

        assertEquals(0xFF0000FF.toInt(), state.page.timed.single().color)
    }

    @Test
    fun `events of a hidden calendar are not drawn`() = runTest {
        add(work, "Shown", 8)
        add(home, "Hidden", 9)
        repository.saveSettings(home.id, CalendarSettings(visible = false))

        val state = viewModel().page(range).first()

        assertEquals(listOf("Shown"), state.page.timed.map { it.instance.title })
    }

    @Test
    fun `an unanswered invitation is marked pending`() = runTest {
        source.create(
            EventDraft(
                calendarId = work.id,
                title = "Invitation",
                time = EventTime.AllDay(day, day.plusDays(1)),
                attendees = listOf(
                    Attendee.of("me@example.com", status = AttendeeStatus.NEEDS_ACTION)
                )
            )
        )

        val state = viewModel().page(range).first()

        val bar = state.page.allDay.single()
        assertEquals(AttendeeStatus.NEEDS_ACTION, bar.instance.selfStatus)
        assertTrue(bar.isPending)
    }

    @Test
    fun `a page follows changes in the source`() = runTest {
        val model = viewModel()

        model.page(range).test {
            assertTrue(awaitItem().page.timed.isEmpty())
            add(work, "Late addition", 14)
            val updated = awaitItem()
            assertEquals(listOf("Late addition"), updated.page.timed.map { it.instance.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a source that fails gives an empty page flagged as failed`() = runTest {
        val failing = mockk<CalendarSource>()
        every { failing.changes } returns emptyFlow()
        coEvery { failing.calendars() } returns
            CalendarResult.Failure(CalendarError.PermissionDenied)
        val model = viewModel(
            CalendarRepository(failing, database.calendarSettingsDao(), Dispatchers.Unconfined)
        )

        val state = model.page(range).first()

        assertTrue(state.failed)
        assertEquals(listOf(day), state.page.days)
        assertTrue(state.page.timed.isEmpty())
    }

    @Test
    fun `the empty page has the days of the range and nothing else`() {
        val state = viewModel().emptyPage(DateRange(day, day.plusDays(3)))

        assertEquals(3, state.page.days.size)
        assertTrue(state.page.timed.isEmpty() && state.page.allDay.isEmpty())
        assertFalse(state.failed)
    }

    @Test
    fun `now carries the clock's instant and the device zone`() = runTest {
        viewModel().now.test {
            val now = awaitItem()
            assertEquals(Instant.parse("2026-03-11T09:30:30Z"), now.instant)
            assertEquals(madrid, now.zone)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
