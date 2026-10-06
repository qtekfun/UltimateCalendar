// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReminderAndEventTest {
    private val timed = EventTime.Timed(
        Instant.parse("2026-10-06T10:00:00Z"),
        Instant.parse("2026-10-06T11:00:00Z"),
        ZoneId.of("UTC")
    )

    private fun event(time: EventTime, rrule: String? = null) =
        Event(EventId(1), CalendarId(2), "Title", time, rrule = rrule)

    @Test
    fun `a reminder cannot be negative`() {
        assertThrows(IllegalArgumentException::class.java) { Reminder(-1) }
        Reminder(0)
    }

    @Test
    fun `an event is recurring only with a rule`() {
        assertFalse(event(timed).isRecurring)
        assertTrue(event(timed, "FREQ=DAILY").isRecurring)
    }

    @Test
    fun `an event is all-day when its time is a date range`() {
        val day = EventTime.AllDay(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7))
        assertTrue(event(day).isAllDay)
        assertFalse(event(timed).isAllDay)
    }
}
