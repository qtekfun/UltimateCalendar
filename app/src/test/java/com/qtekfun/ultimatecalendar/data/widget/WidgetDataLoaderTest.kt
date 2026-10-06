// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.widget

import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.CalendarSettings
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.navigation.FirstDayOfWeekSource
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.widget.AgendaWidgetRow
import com.qtekfun.ultimatecalendar.notify.SystemZone
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WidgetDataLoaderTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val clock = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneId.of("UTC"))
    private val account = CalendarAccount("me@example.com", "com.google")
    private val work = calendar(1, visible = true)
    private val archive = calendar(2, visible = false)

    private lateinit var database: UltimateCalendarDatabase
    private lateinit var source: FakeCalendarSource
    private lateinit var repository: CalendarRepository

    private fun calendar(id: Long, visible: Boolean) = CalendarInfo(
        CalendarId(id),
        account,
        "Calendar $id",
        0xFF0B63CE.toInt(),
        CalendarAccess.OWNER,
        visible,
        "me@example.com"
    )

    private fun loader(repository: CalendarRepository = this.repository) = WidgetDataLoader(
        repository,
        clock,
        SystemZone { zone },
        FirstDayOfWeekSource { DayOfWeek.MONDAY }
    )

    @BeforeEach
    fun open() {
        database = inMemoryDatabase()
        source = FakeCalendarSource(listOf(work, archive))
        repository =
            CalendarRepository(source, database.calendarSettingsDao(), Dispatchers.Unconfined)
    }

    @AfterEach
    fun close() = database.close()

    private suspend fun add(calendar: CalendarInfo, title: String, start: String) {
        val from = Instant.parse(start)
        source.create(
            EventDraft(calendar.id, title, EventTime.Timed(from, from.plusSeconds(3600), zone))
        )
    }

    @Test
    fun `the agenda lists the next events of the visible calendars only`() = runTest {
        add(work, "Standup", "2026-10-07T07:00:00Z")
        add(archive, "Hidden", "2026-10-07T08:00:00Z")

        val loaded = loader().agenda() as WidgetLoad.Loaded
        val titles = loaded.value.rows.filterIsInstance<AgendaWidgetRow.Event>()
            .map { it.entry.instance.title }

        assertEquals(LocalDate.parse("2026-10-06"), loaded.value.today)
        assertEquals(listOf("Standup"), titles)
    }

    @Test
    fun `a calendar the user switched on locally shows its events`() = runTest {
        add(archive, "Shown now", "2026-10-07T08:00:00Z")
        repository.saveSettings(archive.id, CalendarSettings(visible = true))

        val loaded = loader().agenda() as WidgetLoad.Loaded

        assertEquals(1, loaded.value.rows.filterIsInstance<AgendaWidgetRow.Event>().size)
    }

    @Test
    fun `the agenda is empty when there is nothing to show`() = runTest {
        val loaded = loader().agenda() as WidgetLoad.Loaded

        assertTrue(loaded.value.rows.isEmpty())
    }

    @Test
    fun `the month marks the days of its events in the calendar color`() = runTest {
        add(work, "Dentist", "2026-10-15T08:00:00Z")

        val loaded = loader().month(offset = 0, markers = 3) as WidgetLoad.Loaded
        val day = loaded.value.weeks.flatten().first { it.date == LocalDate.parse("2026-10-15") }

        assertEquals(YearMonth.of(2026, 10), loaded.value.month)
        assertEquals(listOf(0xFF0B63CE.toInt()), day.markers.map { it.color })
    }

    @Test
    fun `the month follows the offset and reads that month's events`() = runTest {
        add(work, "Next month", "2026-11-10T08:00:00Z")

        val loaded = loader().month(offset = 1, markers = 1) as WidgetLoad.Loaded
        val day = loaded.value.weeks.flatten().first { it.date == LocalDate.parse("2026-11-10") }

        assertEquals(YearMonth.of(2026, 11), loaded.value.month)
        assertEquals(1, day.eventCount)
    }

    private fun failing(error: CalendarError, calendarsFail: Boolean): CalendarRepository {
        val broken = mockk<CalendarSource>()
        every { broken.changes } returns emptyFlow()
        if (calendarsFail) {
            coEvery { broken.calendars() } returns CalendarResult.Failure(error)
        } else {
            coEvery { broken.calendars() } returns CalendarResult.Success(listOf(work))
            coEvery { broken.instances(any(), any()) } returns CalendarResult.Failure(error)
        }
        return CalendarRepository(broken, database.calendarSettingsDao(), Dispatchers.Unconfined)
    }

    @Test
    fun `a missing permission is told apart from other failures`() = runTest {
        val denied = CalendarError.PermissionDenied
        val gone = CalendarError.SourceFailure("gone")

        assertEquals(WidgetLoad.NoPermission, loader(failing(denied, true)).agenda())
        assertEquals(WidgetLoad.NoPermission, loader(failing(denied, false)).agenda())
        assertEquals(WidgetLoad.NoPermission, loader(failing(denied, true)).month(0, 3))
        assertEquals(WidgetLoad.Failed, loader(failing(gone, true)).agenda())
        assertEquals(WidgetLoad.Failed, loader(failing(gone, false)).month(0, 3))
    }
}
