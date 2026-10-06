// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EventTimeTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun `a timed event cannot end before it starts`() {
        val start = Instant.parse("2026-10-06T10:00:00Z")
        assertThrows(IllegalArgumentException::class.java) {
            EventTime.Timed(start, start.minusSeconds(1), madrid)
        }
        // A zero-length event is allowed (a point in time).
        EventTime.Timed(start, start, madrid)
    }

    @Test
    fun `an all-day event lasts at least one day and its end is exclusive`() {
        val day = LocalDate.of(2026, 10, 6)
        assertThrows(IllegalArgumentException::class.java) { EventTime.AllDay(day, day) }
        val twoDays = EventTime.AllDay(day, day.plusDays(2))
        assertEquals(day.plusDays(1), twoDays.lastDate)
    }

    @Test
    fun `an all-day event starts at midnight of the zone asked for`() {
        val day = EventTime.AllDay(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7))
        assertEquals(Instant.parse("2026-10-05T22:00:00Z"), day.startIn(madrid))
        assertEquals(Instant.parse("2026-10-05T15:00:00Z"), day.startIn(tokyo))
        assertEquals(Instant.parse("2026-10-06T22:00:00Z"), day.endIn(madrid))
    }

    @Test
    fun `a timed event keeps its instants whatever zone is asked for`() {
        val timed = EventTime.Timed(
            Instant.parse("2026-10-06T10:00:00Z"),
            Instant.parse("2026-10-06T11:00:00Z"),
            madrid
        )
        assertEquals(timed.start, timed.startIn(tokyo))
        assertEquals(timed.end, timed.endIn(tokyo))
    }

    @Test
    fun `all-day dates survive a daylight saving change`() {
        // Spain returns to winter time on 2026-10-25: that day lasts 25 hours.
        val day = EventTime.AllDay(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26))
        val hours = java.time.Duration.between(day.startIn(madrid), day.endIn(madrid)).toHours()
        assertEquals(25, hours)
    }
}
