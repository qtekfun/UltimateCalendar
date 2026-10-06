// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EventDraftTest {
    private val start = Instant.parse("2026-10-06T10:00:00Z")

    @Test
    fun `toEvent keeps every field and adds id and organizer`() {
        val draft = EventDraft(
            calendarId = CalendarId(3),
            title = "Lunch",
            time = EventTime.Timed(start, start.plusSeconds(3600), ZoneId.of("UTC")),
            location = "Cafe",
            description = "Notes",
            color = 0xFF112233.toInt(),
            availability = Availability.FREE,
            rrule = "FREQ=DAILY;COUNT=2",
            attendees = listOf(Attendee.of("ana@example.com")),
            reminders = listOf(Reminder(10))
        )
        val event = draft.toEvent(EventId(9), organizer = "me@example.com")
        assertEquals(
            Event(
                id = EventId(9),
                calendarId = CalendarId(3),
                title = "Lunch",
                time = draft.time,
                location = "Cafe",
                description = "Notes",
                color = 0xFF112233.toInt(),
                availability = Availability.FREE,
                rrule = "FREQ=DAILY;COUNT=2",
                organizer = "me@example.com",
                attendees = draft.attendees,
                reminders = draft.reminders
            ),
            event
        )
    }

    @Test
    fun `a time range must last some time`() {
        assertThrows(IllegalArgumentException::class.java) { TimeRange(start, start) }
        TimeRange(start, start.plusSeconds(1))
    }
}
