// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.month

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MonthGridTest {
    private fun grid(month: String, first: DayOfWeek) = MonthGrid.of(YearMonth.parse(month), first)

    @Test
    fun `a month that fits five Monday weeks has five rows`() {
        val grid = grid("2026-10", DayOfWeek.MONDAY)

        assertEquals(5, grid.weeks.size)
        assertEquals(LocalDate.parse("2026-09-28"), grid.days.first())
        assertEquals(LocalDate.parse("2026-11-01"), grid.days.last())
    }

    @Test
    fun `a month that spills over six weeks has six rows`() {
        // August 2026 starts on a Saturday and ends on a Monday.
        assertEquals(6, grid("2026-08", DayOfWeek.MONDAY).weeks.size)
        assertEquals(6, grid("2026-08", DayOfWeek.SUNDAY).weeks.size)
    }

    @Test
    fun `the first day of the week decides the rows`() {
        // February 2026 starts on a Sunday and has 28 days.
        val sunday = grid("2026-02", DayOfWeek.SUNDAY)
        val monday = grid("2026-02", DayOfWeek.MONDAY)

        assertEquals(4, sunday.weeks.size)
        assertEquals(LocalDate.parse("2026-02-01"), sunday.days.first())
        assertEquals(5, monday.weeks.size)
        assertEquals(LocalDate.parse("2026-01-26"), monday.days.first())
    }

    @Test
    fun `every row has seven consecutive days starting on the first day of the week`() {
        val grid = grid("2026-10", DayOfWeek.SATURDAY)

        grid.weeks.forEach { week ->
            assertEquals(MonthGrid.DAYS_IN_WEEK, week.size)
            assertEquals(DayOfWeek.SATURDAY, week.first().dayOfWeek)
        }
        val expected = generateSequence(grid.days.first()) { it.plusDays(1) }
            .take(grid.days.size)
            .toList()
        assertEquals(expected, grid.days)
    }

    @Test
    fun `a leap February has its 29th in the grid and flags its own days`() {
        val grid = grid("2028-02", DayOfWeek.MONDAY)

        assertTrue(LocalDate.parse("2028-02-29") in grid.days)
        assertTrue(grid.isInMonth(LocalDate.parse("2028-02-29")))
        assertFalse(grid.isInMonth(LocalDate.parse("2028-03-01")))
        assertFalse(grid.isInMonth(LocalDate.parse("2028-01-31")))
        assertEquals(LocalDate.parse("2028-03-06"), grid.range.endExclusive)
    }

    @Test
    fun `a common February is one day shorter`() {
        val grid = grid("2027-02", DayOfWeek.MONDAY)

        assertEquals(4, grid.weeks.size)
        assertEquals(28, grid.days.count { grid.isInMonth(it) })
    }

    @Test
    fun `the range is the whole grid in one read`() {
        val grid = grid("2026-10", DayOfWeek.MONDAY)

        assertEquals(LocalDate.parse("2026-09-28"), grid.range.start)
        assertEquals(LocalDate.parse("2026-11-02"), grid.range.endExclusive)
    }

    @Test
    fun `in the month of a clock change the range keeps the short day`() {
        val grid = grid("2026-03", DayOfWeek.MONDAY)

        val time = grid.range.toTimeRange(ZoneId.of("Europe/Madrid"))

        // Madrid lost an hour on Sunday 29 March.
        assertEquals(grid.days.size * 24L - 1, Duration.between(time.start, time.end).toHours())
    }
}
