// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AllDayLanesTest {
    private val monday = LocalDate.parse("2026-03-09")
    private val week = DateRange(monday, monday.plusDays(7))

    private fun allDay(id: Long, fromDay: Int, lastDay: Int) = EventInstance(
        eventId = EventId(id),
        calendarId = CalendarId(1),
        title = "e$id",
        time = EventTime.AllDay(monday.plusDays(fromDay.toLong()), monday.plusDays(lastDay + 1L))
    )

    private fun page(vararg events: EventInstance) =
        TimeGridLayout.build(week, ZoneId.of("Europe/Madrid"), events.toList())

    @Test
    fun `everything fits so nothing is hidden`() {
        val strip = AllDayLanes.limit(page(allDay(1, 0, 2), allDay(2, 1, 1)), maxRows = 2)
        assertEquals(2, strip.bars.size)
        assertEquals(List(7) { 0 }, strip.hidden)
        assertEquals(2, strip.rows)
        assertFalse(strip.hasOverflow)
    }

    @Test
    fun `an empty week needs no rows`() {
        val strip = AllDayLanes.limit(page(), maxRows = 3)
        assertEquals(0, strip.rows)
        assertTrue(strip.bars.isEmpty())
    }

    @Test
    fun `the last row becomes the plus N row when bars overflow`() {
        // Four bars stacked on Wednesday; three rows allowed: two stay, two are hidden.
        val strip = AllDayLanes.limit(
            page(allDay(1, 2, 2), allDay(2, 2, 2), allDay(3, 2, 2), allDay(4, 2, 2)),
            maxRows = 3
        )
        assertEquals(2, strip.bars.size)
        assertTrue(strip.bars.all { it.row < 2 })
        assertEquals(listOf(0, 0, 2, 0, 0, 0, 0), strip.hidden)
        assertEquals(3, strip.rows)
        assertTrue(strip.hasOverflow)
    }

    @Test
    fun `a hidden multi-day bar counts once on each day it covers`() {
        // The whole-week bar takes row 0 (longest first); the other three stack below it.
        val strip = AllDayLanes.limit(
            page(allDay(1, 0, 6), allDay(2, 1, 3), allDay(3, 2, 4), allDay(4, 3, 3)),
            maxRows = 2
        )
        assertEquals(listOf(1L), strip.bars.map { it.instance.eventId.value })
        assertEquals(listOf(0, 1, 2, 3, 1, 0, 0), strip.hidden)
    }

    @Test
    fun `a single row leaves only the counts`() {
        val strip = AllDayLanes.limit(page(allDay(1, 0, 1), allDay(2, 1, 1)), maxRows = 1)
        assertTrue(strip.bars.isEmpty())
        assertEquals(listOf(1, 2, 0, 0, 0, 0, 0), strip.hidden)
        assertEquals(1, strip.rows)
    }

    @Test
    fun `exactly as many rows as allowed is not an overflow`() {
        val strip = AllDayLanes.limit(page(allDay(1, 0, 0), allDay(2, 0, 0)), maxRows = 2)
        assertEquals(2, strip.bars.size)
        assertFalse(strip.hasOverflow)
    }

    @Test
    fun `it needs at least one row`() {
        assertThrows(IllegalArgumentException::class.java) { AllDayLanes.limit(page(), 0) }
    }
}
