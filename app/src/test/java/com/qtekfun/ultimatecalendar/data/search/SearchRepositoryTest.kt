// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.search

import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.data.source.UnavailableCalendarSource
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
import com.qtekfun.ultimatecalendar.notify.SystemZone
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SearchRepositoryTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-10-06T09:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val account = CalendarAccount("me@example.com", "com.google")
    private val work = calendar(1, "Work", 0xFF112233.toInt())
    private val home = calendar(2, "Home", 0xFF445566.toInt())

    private lateinit var database: UltimateCalendarDatabase
    private lateinit var source: FakeCalendarSource
    private lateinit var calendars: CalendarRepository

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
        database = inMemoryDatabase()
        source = FakeCalendarSource(listOf(work, home))
        calendars =
            CalendarRepository(source, database.calendarSettingsDao(), Dispatchers.Unconfined)
    }

    @AfterEach
    fun close() {
        database.close()
    }

    private fun repository(
        on: CalendarSource = source,
        repository: CalendarRepository = calendars
    ) = SearchRepository(on, repository, clock, SystemZone { madrid }, Dispatchers.Unconfined)

    private suspend fun add(
        calendar: CalendarInfo,
        title: String,
        start: String,
        rrule: String? = null
    ) = source.create(
        EventDraft(
            calendarId = calendar.id,
            title = title,
            time = Instant.parse(start).let { EventTime.Timed(it, it.plusSeconds(3_600), madrid) },
            rrule = rrule
        )
    )

    private suspend fun SearchRepository.titles(
        query: String,
        includeHidden: Boolean = false,
        range: TimeRange? = null
    ): List<String> {
        val outcome = (search(query, includeHidden, range) as CalendarResult.Success).value
        return (outcome.sections.upcoming + outcome.sections.past).map { it.match.event.title }
    }

    @Test
    fun `events from every visible calendar are found and split by time`() = runTest {
        add(work, "Budget soon", "2026-10-08T09:00:00Z")
        add(home, "Budget old", "2026-09-01T09:00:00Z")
        add(home, "Lunch", "2026-10-08T12:00:00Z")

        val outcome = (repository().search("budget", false) as CalendarResult.Success).value

        assertEquals(listOf("Budget soon"), outcome.sections.upcoming.map { it.match.event.title })
        assertEquals(listOf("Budget old"), outcome.sections.past.map { it.match.event.title })
        assertEquals(
            mapOf(work.id to work.color, home.id to home.color),
            outcome.calendarColors
        )
    }

    @Test
    fun `hidden calendars are left out unless asked for`() = runTest {
        add(work, "Budget work", "2026-10-08T09:00:00Z")
        add(home, "Budget home", "2026-10-09T09:00:00Z")
        calendars.saveSettings(home.id, CalendarSettings(visible = false))

        assertEquals(listOf("Budget work"), repository().titles("budget"))
        assertEquals(
            listOf("Budget work", "Budget home"),
            repository().titles("budget", includeHidden = true)
        )
    }

    @Test
    fun `with every calendar hidden nothing is asked of the source`() = runTest {
        add(work, "Budget", "2026-10-08T09:00:00Z")
        calendars.saveSettings(work.id, CalendarSettings(visible = false))
        calendars.saveSettings(home.id, CalendarSettings(visible = false))

        assertEquals(emptyList<String>(), repository().titles("budget"))
        assertEquals(listOf("Budget"), repository().titles("budget", includeHidden = true))
    }

    @Test
    fun `a blank query finds nothing`() = runTest {
        add(work, "Budget", "2026-10-08T09:00:00Z")

        assertEquals(emptyList<String>(), repository().titles("   "))
    }

    @Test
    fun `a series shows its next occurrence`() = runTest {
        add(work, "Gym", "2026-10-01T07:00:00Z", rrule = "FREQ=WEEKLY;COUNT=4")

        val outcome = (repository().search("gym", false) as CalendarResult.Success).value

        val shown = outcome.sections.upcoming.single()
        assertEquals(Instant.parse("2026-10-08T07:00:00Z"), shown.instance.time.startIn(madrid))
        assertTrue(outcome.sections.past.isEmpty())
    }

    @Test
    fun `a series that is over shows its last occurrence`() = runTest {
        add(work, "Course", "2026-09-01T07:00:00Z", rrule = "FREQ=WEEKLY;COUNT=3")

        val outcome = (repository().search("course", false) as CalendarResult.Success).value

        assertEquals(
            Instant.parse("2026-09-15T07:00:00Z"),
            outcome.sections.past.single().instance.time.startIn(madrid)
        )
    }

    @Test
    fun `a range limits the events and is where the occurrences are read`() = runTest {
        add(work, "Gym", "2026-10-01T07:00:00Z", rrule = "FREQ=WEEKLY;COUNT=4")
        add(work, "Gym once", "2026-12-01T07:00:00Z")
        val range =
            TimeRange(Instant.parse("2026-10-14T00:00:00Z"), Instant.parse("2026-10-16T00:00:00Z"))

        val outcome = (repository().search("gym", false, range) as CalendarResult.Success).value

        assertEquals(
            listOf(Instant.parse("2026-10-15T07:00:00Z")),
            outcome.sections.upcoming.map { it.instance.time.startIn(madrid) }
        )
        assertEquals(1, outcome.sections.size)
    }

    @Test
    fun `when the occurrences cannot be read the series is still listed at its first one`() =
        runTest {
            add(work, "Gym", "2026-10-01T07:00:00Z", rrule = "FREQ=WEEKLY;COUNT=4")
            val failing = object : CalendarSource by source {
                override suspend fun instances(
                    range: TimeRange,
                    calendarIds: Set<CalendarId>?
                ): CalendarResult<List<EventInstance>> =
                    CalendarResult.Failure(CalendarError.SourceFailure("down"))
            }

            val outcome = (repository(failing).search("gym", false) as CalendarResult.Success).value

            assertEquals(
                Instant.parse("2026-10-01T07:00:00Z"),
                outcome.sections.past.single().instance.time.startIn(madrid)
            )
        }

    @Test
    fun `a failing source is reported`() = runTest {
        val failing = repository(UnavailableCalendarSource)

        assertEquals(
            CalendarResult.Failure(CalendarError.SourceFailure("no calendar source is bound")),
            failing.search("budget", false)
        )
    }

    @Test
    fun `a failing search of the events is reported`() = runTest {
        val failing = object : CalendarSource by source {
            override suspend fun search(
                query: String,
                calendarIds: Set<CalendarId>?,
                range: TimeRange?
            ) = CalendarResult.Failure(CalendarError.PermissionDenied)
        }

        assertEquals(
            CalendarResult.Failure(CalendarError.PermissionDenied),
            repository(failing).search("budget", false)
        )
    }
}
