// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
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

class NotifiedInvitationsTest {
    private lateinit var database: UltimateCalendarDatabase
    private lateinit var notified: NotifiedInvitations

    @BeforeEach
    fun open() {
        database = inMemoryDatabase()
        notified = NotifiedInvitations(database.notifiedInvitationDao(), Dispatchers.Unconfined)
    }

    @AfterEach
    fun close() = database.close()

    private fun timed(event: Long, calendar: Long = 1) = Invitation(
        key = InvitationKey(CalendarId(calendar), EventId(event)),
        title = "Lunch $event",
        time = EventTime.Timed(
            Instant.parse("2026-06-10T10:00:00Z"),
            Instant.parse("2026-06-10T11:30:00Z"),
            ZoneId.of("Europe/Madrid")
        ),
        location = "Cafe",
        organizer = "boss@example.com"
    )

    private fun allDay(event: Long) = Invitation(
        key = InvitationKey(CalendarId(1), EventId(event)),
        title = "Trip",
        time = EventTime.AllDay(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 4))
    )

    @Test
    fun `timed and all-day invitations come back exactly as stored`() = runTest {
        val stored = listOf(timed(1), allDay(2))

        notified.replaceAll(stored)

        assertEquals(stored.toSet(), notified.load().toSet())
    }

    @Test
    fun `the same event id in two calendars is two invitations`() = runTest {
        notified.replaceAll(listOf(timed(1, calendar = 1), timed(1, calendar = 2)))

        assertEquals(2, notified.load().size)
    }

    @Test
    fun `replacing swaps the whole set`() = runTest {
        notified.replaceAll(listOf(timed(1), timed(2)))

        notified.replaceAll(listOf(timed(3)))

        assertEquals(listOf(timed(3)), notified.load())
    }

    @Test
    fun `a replacement that fails halfway keeps the previous set`() = runTest {
        notified.replaceAll(listOf(timed(1)))

        // The same key twice breaks the insert after the old rows were deleted.
        assertThrows<Exception> { notified.replaceAll(listOf(timed(2), timed(2))) }

        assertEquals(listOf(timed(1)), notified.load())
    }
}
