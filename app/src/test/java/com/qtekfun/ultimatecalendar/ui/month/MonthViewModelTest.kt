// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.month

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
import com.qtekfun.ultimatecalendar.domain.month.MonthBarStyle
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.SystemZone
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
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
class MonthViewModelTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val clock = Clock.fixed(Instant.parse("2026-10-06T12:20:00Z"), ZoneId.of("UTC"))
    private val october = YearMonth.parse("2026-10")
    private val account = CalendarAccount("me@example.com", "com.google")
    private val work = calendar(1, "Work", 0xFF112233.toInt())
    private val home = calendar(2, "Home", 0xFF445566.toInt())

    private lateinit var database: UltimateCalendarDatabase
    private lateinit var source: FakeCalendarSource
    private lateinit var repository: CalendarRepository

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
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
        MonthViewModel(repository, clock, SystemZone { madrid }, Dispatchers.Unconfined)

    private suspend fun addTimed(calendar: CalendarInfo, title: String, day: String, hour: Int) =
        source.create(
            EventDraft(
                calendarId = calendar.id,
                title = title,
                time = EventTime.Timed(
                    LocalDate.parse(day).atTime(hour, 0).atZone(madrid).toInstant(),
                    LocalDate.parse(day).atTime(hour + 1, 0).atZone(madrid).toInstant(),
                    madrid
                )
            )
        )

    private suspend fun addAllDay(calendar: CalendarInfo, title: String, from: String, to: String) =
        source.create(
            EventDraft(
                calendarId = calendar.id,
                title = title,
                time = EventTime.AllDay(LocalDate.parse(from), LocalDate.parse(to))
            )
        )

    @Test
    fun `a page lists the events of the whole grid, neighbouring months included`() = runTest {
        addTimed(work, "In month", "2026-10-07", 9)
        addTimed(home, "From September", "2026-09-30", 10)
        addTimed(home, "In November", "2026-11-01", 11)
        addTimed(home, "Out of the grid", "2026-11-20", 11)

        val state = viewModel().page(october, DayOfWeek.MONDAY).first()

        assertFalse(state.failed)
        assertEquals(
            setOf("In month", "From September", "In November"),
            state.page.weeks.flatMap { it.bars }.map { it.instance.title }.toSet()
        )
    }

    @Test
    fun `the first day of the week changes the grid it reads`() = runTest {
        addTimed(work, "Sunday", "2026-09-27", 9)

        val monday = viewModel().page(october, DayOfWeek.MONDAY).first()
        val sunday = viewModel().page(october, DayOfWeek.SUNDAY).first()

        assertTrue(monday.page.weeks.all { it.bars.isEmpty() })
        assertEquals("Sunday", sunday.page.weeks.first().bars.single().instance.title)
    }

    @Test
    fun `a multi-day event is cut across the rows it crosses`() = runTest {
        addAllDay(work, "Trip", "2026-10-03", "2026-10-07")

        val weeks = viewModel().page(october, DayOfWeek.MONDAY).first().page.weeks

        assertTrue(weeks[0].bars.single().continuesAfter)
        assertTrue(weeks[1].bars.single().continuesBefore)
        assertEquals(MonthBarStyle.BAR, weeks[1].bars.single().style)
    }

    @Test
    fun `an event takes its calendar's color`() = runTest {
        addTimed(work, "Plain", "2026-10-07", 8)
        addTimed(home, "Other", "2026-10-07", 10)

        val bars = viewModel().page(october, DayOfWeek.MONDAY).first().page.weeks[1].bars

        assertEquals(
            mapOf("Plain" to 0xFF112233.toInt(), "Other" to 0xFF445566.toInt()),
            bars.associate { it.instance.title to it.color }
        )
    }

    @Test
    fun `a local calendar color override reaches the events`() = runTest {
        addTimed(work, "Plain", "2026-10-07", 8)
        repository.saveSettings(work.id, CalendarSettings(color = 0xFF0000FF.toInt()))

        val bar = viewModel().page(october, DayOfWeek.MONDAY).first().page.weeks[1].bars.single()

        assertEquals(0xFF0000FF.toInt(), bar.color)
    }

    @Test
    fun `events of a hidden calendar are not drawn`() = runTest {
        addTimed(work, "Shown", "2026-10-07", 8)
        addTimed(home, "Hidden", "2026-10-07", 9)
        repository.saveSettings(home.id, CalendarSettings(visible = false))

        val bars = viewModel().page(october, DayOfWeek.MONDAY).first().page.weeks[1].bars

        assertEquals(listOf("Shown"), bars.map { it.instance.title })
    }

    @Test
    fun `an unanswered invitation is marked pending`() = runTest {
        source.create(
            EventDraft(
                calendarId = work.id,
                title = "Invitation",
                time = EventTime.AllDay(
                    LocalDate.parse("2026-10-07"),
                    LocalDate.parse("2026-10-08")
                ),
                attendees = listOf(
                    Attendee.of("me@example.com", status = AttendeeStatus.NEEDS_ACTION)
                )
            )
        )

        val bar = viewModel().page(october, DayOfWeek.MONDAY).first().page.weeks[1].bars.single()

        assertTrue(bar.isPending)
    }

    @Test
    fun `a page follows changes in the source`() = runTest {
        viewModel().page(october, DayOfWeek.MONDAY).test {
            assertTrue(awaitItem().page.weeks.all { it.bars.isEmpty() })
            addTimed(work, "Late addition", "2026-10-07", 14)
            // The calendars and the instances may both report the change: skip repeats.
            var updated = awaitItem()
            while (updated.page.weeks.all { it.bars.isEmpty() }) updated = awaitItem()
            assertEquals(
                listOf("Late addition"),
                updated.page.weeks.flatMap { it.bars }.map { it.instance.title }
            )
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

        val state = model.page(october, DayOfWeek.MONDAY).first()

        assertTrue(state.failed)
        assertEquals(5, state.page.weeks.size)
        assertTrue(state.page.weeks.all { it.bars.isEmpty() })
    }

    @Test
    fun `the empty page has the rows of the month and nothing else`() {
        val state = viewModel().emptyPage(YearMonth.parse("2026-08"), DayOfWeek.MONDAY)

        assertEquals(6, state.page.weeks.size)
        assertTrue(state.page.weeks.all { it.bars.isEmpty() })
        assertFalse(state.failed)
    }

    @Test
    fun `a long press today starts at the next full hour in the device zone`() {
        // 12:20 UTC is 14:20 in Madrid.
        assertEquals(
            LocalDateTime.parse("2026-10-06T15:00:00"),
            viewModel().quickCreateAt(LocalDate.parse("2026-10-06"))
        )
        assertEquals(
            LocalDateTime.parse("2026-10-20T09:00:00"),
            viewModel().quickCreateAt(LocalDate.parse("2026-10-20"))
        )
    }

    @Test
    fun `the zone is the device's`() {
        assertEquals(madrid, viewModel().zone())
    }
}
