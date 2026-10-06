// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.calendar

import app.cash.turbine.test
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
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarRepositoryTest {
    private val account = CalendarAccount("me@example.com", "com.google")
    private val work = calendar(1, "Work", CalendarAccess.OWNER)
    private val holidays = calendar(2, "Holidays", CalendarAccess.READ)
    private val hidden = calendar(3, "Archive", CalendarAccess.OWNER, visible = false)
    private val start = Instant.parse("2026-03-10T09:00:00Z")
    private val range =
        TimeRange(Instant.parse("2026-03-10T00:00:00Z"), Instant.parse("2026-03-11T00:00:00Z"))

    private lateinit var database: UltimateCalendarDatabase
    private lateinit var source: FakeCalendarSource
    private lateinit var repository: CalendarRepository

    @BeforeEach
    fun open() {
        database = inMemoryDatabase()
        source = FakeCalendarSource(listOf(work, holidays, hidden))
        repository =
            CalendarRepository(source, database.calendarSettingsDao(), Dispatchers.Unconfined)
    }

    @AfterEach
    fun close() = database.close()

    private fun calendar(id: Long, name: String, access: CalendarAccess, visible: Boolean = true) =
        CalendarInfo(
            CalendarId(id),
            account,
            name,
            0xFF0B63CE.toInt(),
            access,
            visible,
            "me@example.com"
        )

    private suspend fun addEvent(calendar: CalendarInfo, title: String) = source.create(
        EventDraft(
            calendar.id,
            title,
            EventTime.Timed(start, start.plusSeconds(3600), ZoneId.of("UTC"))
        )
    )

    private fun <T> CalendarResult<T>.value(): T = (this as CalendarResult.Success).value

    @Test
    fun `calendars lists hidden ones too, as the source gives them`() = runTest {
        repository.calendars().test {
            assertEquals(listOf(work, holidays, hidden), awaitItem().value())
        }
    }

    @Test
    fun `visible calendars leave out the ones the source hides`() = runTest {
        repository.visibleCalendars().test {
            assertEquals(listOf(work, holidays), awaitItem().value())
        }
    }

    @Test
    fun `local overrides replace name, color and visibility and follow later changes`() = runTest {
        repository.calendars().test {
            awaitItem()

            repository.saveSettings(
                hidden.id,
                CalendarSettings(displayName = "Old", color = 7, visible = true)
            )
            val shown = awaitItem().value().first { it.id == hidden.id }
            assertEquals(hidden.copy(displayName = "Old", color = 7, visible = true), shown)

            repository.saveSettings(work.id, CalendarSettings(visible = false))
            assertEquals(false, awaitItem().value().first { it.id == work.id }.visible)

            repository.saveSettings(hidden.id, CalendarSettings())
            assertEquals(hidden, awaitItem().value().first { it.id == hidden.id })
        }
        assertEquals(CalendarSettings(), repository.settings(hidden.id))
        assertEquals(CalendarSettings(visible = false), repository.settings(work.id))
    }

    @Test
    fun `visible calendars follow a local override that shows a hidden one`() = runTest {
        repository.visibleCalendars().test {
            awaitItem()

            repository.saveSettings(hidden.id, CalendarSettings(visible = true))

            assertEquals(listOf(work, holidays, hidden.copy(visible = true)), awaitItem().value())
        }
    }

    @Test
    fun `instances come from the visible calendars only`() = runTest {
        addEvent(work, "Standup")
        addEvent(hidden, "Old stuff")

        repository.instances(range).test {
            assertEquals(listOf("Standup"), awaitItem().value().map(EventInstance::title))
        }
    }

    @Test
    fun `instances are read again when the source reports a change`() = runTest {
        repository.instances(range).test {
            assertEquals(emptyList<EventInstance>(), awaitItem().value())

            addEvent(work, "Standup")
            assertEquals(listOf("Standup"), awaitItem().value().map(EventInstance::title))

            addEvent(work, "Retro")
            assertEquals(
                setOf("Standup", "Retro"),
                awaitItem().value().map(EventInstance::title).toSet()
            )
        }
    }

    @Test
    fun `hiding a calendar locally removes its instances`() = runTest {
        addEvent(work, "Standup")

        repository.instances(range).test {
            assertEquals(listOf("Standup"), awaitItem().value().map(EventInstance::title))

            repository.saveSettings(work.id, CalendarSettings(visible = false))

            assertEquals(emptyList<EventInstance>(), awaitItem().value())
        }
    }

    @Test
    fun `instances are empty, not everything, when no calendar is visible`() = runTest {
        addEvent(hidden, "Old stuff")
        repository.saveSettings(work.id, CalendarSettings(visible = false))
        repository.saveSettings(holidays.id, CalendarSettings(visible = false))

        repository.instances(range).test {
            assertEquals(emptyList<EventInstance>(), awaitItem().value())
        }
    }

    @Test
    fun `the default calendar is the first visible one that accepts events`() = runTest {
        repository.defaultCalendar().test {
            assertEquals(work, awaitItem().value())
        }
    }

    @Test
    fun `the default calendar is the chosen one while it still accepts events`() = runTest {
        val other = calendar(4, "Family", CalendarAccess.EDIT)
        source.addCalendar(other)

        repository.defaultCalendar().test {
            assertEquals(work, awaitItem().value())

            repository.setDefaultCalendar(other.id)
            assertEquals(other, awaitItem().value())

            repository.setDefaultCalendar(holidays.id)
            assertEquals(work, awaitItem().value())

            repository.setDefaultCalendar(null)
            assertEquals(work, awaitItem().value())
        }
    }

    @Test
    fun `the default calendar is the first writable one when every writable calendar is hidden`() =
        runTest {
            repository.saveSettings(work.id, CalendarSettings(visible = false))

            repository.defaultCalendar().test {
                val found = awaitItem().value()
                assertEquals(work.id, found.id)
            }
        }

    @Test
    fun `the default calendar prefers a visible one over a hidden one`() = runTest {
        repository.saveSettings(work.id, CalendarSettings(visible = false))
        source.addCalendar(calendar(5, "Visible", CalendarAccess.CONTRIBUTE))

        repository.defaultCalendar().test {
            assertEquals(CalendarId(5), awaitItem().value().id)
        }
    }

    @Test
    fun `the default calendar fails with not found when none accepts events`() = runTest {
        val readOnly = FakeCalendarSource(listOf(holidays))
        val readOnlyRepository =
            CalendarRepository(readOnly, database.calendarSettingsDao(), Dispatchers.Unconfined)

        readOnlyRepository.defaultCalendar().test {
            assertEquals(CalendarResult.Failure(CalendarError.NotFound), awaitItem())
        }
    }

    @Test
    fun `the default calendar shows the local name and color`() = runTest {
        repository.saveSettings(work.id, CalendarSettings(displayName = "Job"))

        repository.defaultCalendar().test {
            assertEquals("Job", awaitItem().value().displayName)
        }
    }

    @Test
    fun `source failures reach every flow as failures`() = runTest {
        val failure = CalendarResult.Failure(CalendarError.PermissionDenied)
        val broken = mockk<CalendarSource>()
        every { broken.changes } returns emptyFlow()
        coEvery { broken.calendars() } returns failure
        val failing =
            CalendarRepository(broken, database.calendarSettingsDao(), Dispatchers.Unconfined)

        failing.calendars().test { assertEquals(failure, awaitItem()) }
        failing.visibleCalendars().test { assertEquals(failure, awaitItem()) }
        failing.instances(range).test { assertEquals(failure, awaitItem()) }
        failing.defaultCalendar().test { assertEquals(failure, awaitItem()) }
    }

    @Test
    fun `an instance read failure is passed on`() = runTest {
        val failure = CalendarResult.Failure(CalendarError.SourceFailure("gone"))
        val broken = mockk<CalendarSource>()
        every { broken.changes } returns emptyFlow()
        coEvery { broken.calendars() } returns CalendarResult.Success(listOf(work))
        coEvery { broken.instances(range, setOf(work.id)) } returns failure
        val failing =
            CalendarRepository(broken, database.calendarSettingsDao(), Dispatchers.Unconfined)

        failing.instances(range).test {
            assertTrue(awaitItem() == failure)
        }
    }
}
