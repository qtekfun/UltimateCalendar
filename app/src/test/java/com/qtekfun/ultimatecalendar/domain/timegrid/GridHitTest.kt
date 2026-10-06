// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GridHitTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val day = LocalDate.of(2026, 3, 11)

    private fun timed(id: Long, from: LocalDateTime, to: LocalDateTime) = EventInstance(
        EventId(id),
        CalendarId(1),
        "Event $id",
        EventTime.Timed(from.atZone(zone).toInstant(), to.atZone(zone).toInstant(), zone)
    )

    private fun page(days: Long, vararg events: EventInstance) =
        TimeGridLayout.build(DateRange(day, day.plusDays(days)), zone, events.toList())

    private fun at(hour: Int, minute: Int = 0, offset: Long = 0) =
        day.plusDays(offset).atTime(hour, minute)

    @Test
    fun `the block under a point is found by day and minute`() {
        val page = page(3, timed(1, at(9), at(10)), timed(2, at(9, 0, 1), at(10, 0, 1)))

        assertEquals(1L, GridHit.timedAt(page, 0.1f, 9 * 60 + 30f)?.instance?.eventId?.value)
        assertEquals(2L, GridHit.timedAt(page, 0.5f, 9 * 60 + 30f)?.instance?.eventId?.value)
        assertNull(GridHit.timedAt(page, 0.9f, 9 * 60 + 30f))
        assertNull(GridHit.timedAt(page, 0.1f, 10 * 60 + 1f))
        assertNull(GridHit.timedAt(page, 0.1f, 9 * 60 - 1f))
    }

    @Test
    fun `a short event is hit where it is drawn, at least half an hour tall`() {
        val page = page(1, timed(1, at(9), at(9, 10)))

        assertEquals(1L, GridHit.timedAt(page, 0.5f, 9 * 60 + 25f)?.instance?.eventId?.value)
        assertNull(GridHit.timedAt(page, 0.5f, 9 * 60 + 31f))
    }

    @Test
    fun `overlapping events are told apart by their column`() {
        val page = page(1, timed(1, at(9), at(10)), timed(2, at(9), at(10)))

        val left = GridHit.timedAt(page, 0.25f, 9 * 60 + 30f)
        val right = GridHit.timedAt(page, 0.75f, 9 * 60 + 30f)

        assertEquals(1L, left?.instance?.eventId?.value)
        assertEquals(2L, right?.instance?.eventId?.value)
    }

    @Test
    fun `a point past the right edge still belongs to the last day`() {
        val page = page(2, timed(1, at(9, 0, 1), at(10, 0, 1)))

        assertEquals(1L, GridHit.timedAt(page, 1f, 9 * 60 + 30f)?.instance?.eventId?.value)
        assertEquals(1L, GridHit.timedAt(page, 5f, 9 * 60 + 30f)?.instance?.eventId?.value)
    }

    @Test
    fun `the handle is the bottom of the block`() {
        val block = page(1, timed(1, at(9), at(11))).timed.single()

        assertTrue(GridHit.isOnHandle(block, 10 * 60 + 50f, 30f))
        assertTrue(GridHit.isOnHandle(block, 11 * 60 - 1f, 30f))
        assertFalse(GridHit.isOnHandle(block, 10 * 60 + 29f, 30f))
        assertFalse(GridHit.isOnHandle(block, 9 * 60 + 10f, 30f))
    }

    @Test
    fun `the handle never takes more than half of a short block`() {
        val block = page(1, timed(1, at(9), at(10))).timed.single()

        assertTrue(GridHit.isOnHandle(block, 9 * 60 + 31f, 120f))
        assertFalse(GridHit.isOnHandle(block, 9 * 60 + 29f, 120f))
    }

    @Test
    fun `a block that goes on to the next day has no handle`() {
        val block = page(2, timed(1, at(22), at(2, 0, 1))).timed.first { it.dayIndex == 0 }

        assertFalse(GridHit.isOnHandle(block, 24 * 60 - 1f, 30f))
    }

    @Test
    fun `the all day bar is found by day and row`() {
        val first = EventInstance(
            EventId(1),
            CalendarId(1),
            "Trip",
            EventTime.AllDay(day, day.plusDays(2))
        )
        val second = first.copy(eventId = EventId(2), time = EventTime.AllDay(day, day.plusDays(1)))
        val bars = page(3, first, second).allDay

        val top = GridHit.allDayAt(bars, 0, 0)
        val below = GridHit.allDayAt(bars, 0, 1)

        assertEquals(setOf(1L, 2L), setOf(top, below).map { it?.instance?.eventId?.value }.toSet())
        assertEquals(1L, GridHit.allDayAt(bars, 1, 0)?.instance?.eventId?.value)
        assertNull(GridHit.allDayAt(bars, 2, 0))
        assertNull(GridHit.allDayAt(bars, 0, 2))
    }
}
