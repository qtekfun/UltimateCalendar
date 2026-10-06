// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.domain.invitations.AttendedEvent
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AttendedEventsTest {
    private lateinit var database: UltimateCalendarDatabase
    private lateinit var attended: AttendedEvents

    @BeforeEach
    fun open() {
        database = inMemoryDatabase()
        attended = AttendedEvents(database.attendedEventDao(), Dispatchers.Unconfined)
    }

    @AfterEach
    fun close() = database.close()

    private fun timed(event: Long, calendar: Long = 1) = AttendedEvent(
        key = InvitationKey(CalendarId(calendar), EventId(event)),
        title = "Lunch $event",
        time = EventTime.Timed(
            Instant.parse("2026-06-10T10:00:00Z"),
            Instant.parse("2026-06-10T11:30:00Z"),
            ZoneId.of("Europe/Madrid")
        ),
        placeHash = AttendedEvent.placeHash("Cafe")
    )

    private fun allDay(event: Long) = AttendedEvent(
        key = InvitationKey(CalendarId(1), EventId(event)),
        title = "Trip",
        time = EventTime.AllDay(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 4)),
        placeHash = ""
    )

    @Test
    fun `timed and all-day events come back exactly as stored`() = runTest {
        val stored = listOf(timed(1), allDay(2))

        attended.replaceAll(stored)

        assertEquals(stored.toSet(), attended.load().toSet())
    }

    @Test
    fun `nothing is stored at first and the same id in two calendars is two events`() = runTest {
        assertEquals(emptyList<AttendedEvent>(), attended.load())

        attended.replaceAll(listOf(timed(1, calendar = 1), timed(1, calendar = 2)))

        assertEquals(2, attended.load().size)
    }

    @Test
    fun `replacing swaps the whole set, which prunes what is no longer followed`() = runTest {
        attended.replaceAll(listOf(timed(1), timed(2)))

        attended.replaceAll(listOf(timed(3)))

        assertEquals(listOf(timed(3)), attended.load())
    }

    @Test
    fun `a replacement that fails halfway keeps the previous set`() = runTest {
        attended.replaceAll(listOf(timed(1)))

        assertThrows<Exception> { attended.replaceAll(listOf(timed(2), timed(2))) }

        assertEquals(listOf(timed(1)), attended.load())
    }

    @Test
    fun `marking flags only the followed event and can be taken back`() = runTest {
        attended.replaceAll(listOf(timed(1), timed(2)))

        attended.mark(EventId(1))
        attended.mark(EventId(99))

        assertEquals(setOf(timed(1).copy(ownEdit = true), timed(2)), attended.load().toSet())

        attended.mark(EventId(1), marked = false)

        assertEquals(setOf(timed(1), timed(2)), attended.load().toSet())
    }

    @Test
    fun `no record keeps nothing`() = runTest {
        NoAttendedEvents.replaceAll(listOf(timed(1)))

        assertEquals(emptyList<AttendedEvent>(), NoAttendedEvents.load())
    }

    @Test
    fun `the place is stored as a hash and never as text`() = runTest {
        attended.replaceAll(listOf(timed(1)))

        val row = database.attendedEventDao().all().single()

        assertEquals(AttendedEvent.placeHash("Cafe"), row.placeHash)
        assertEquals(false, row.toString().contains("Cafe"))
    }
}
