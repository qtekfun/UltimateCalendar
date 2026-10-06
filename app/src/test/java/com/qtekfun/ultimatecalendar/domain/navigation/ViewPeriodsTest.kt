// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ViewPeriodsTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun range(view: CalendarView, date: String, first: DayOfWeek = DayOfWeek.MONDAY) =
        ViewPeriods.range(view, LocalDate.parse(date), first)

    private fun days(start: String, end: String) =
        DateRange(LocalDate.parse(start), LocalDate.parse(end))

    @Test
    fun `today follows the zone, not the clock's own offset`() {
        val clock = Clock.fixed(Instant.parse("2026-03-10T23:30:00Z"), ZoneId.of("UTC"))
        assertEquals(LocalDate.parse("2026-03-10"), ViewPeriods.today(clock, ZoneId.of("UTC")))
        assertEquals(LocalDate.parse("2026-03-11"), ViewPeriods.today(clock, madrid))
        assertEquals(
            LocalDate.parse("2026-03-10"),
            ViewPeriods.today(clock, ZoneId.of("America/New_York"))
        )
    }

    @Test
    fun `day and three days start on the anchor`() {
        assertEquals(days("2026-03-11", "2026-03-12"), range(CalendarView.DAY, "2026-03-11"))
        assertEquals(
            days("2026-03-11", "2026-03-14"),
            range(CalendarView.THREE_DAYS, "2026-03-11")
        )
    }

    @Test
    fun `a week starts on the first day of the week`() {
        // 2026-03-11 is a Wednesday.
        assertEquals(
            days("2026-03-09", "2026-03-16"),
            range(CalendarView.WEEK, "2026-03-11", DayOfWeek.MONDAY)
        )
        assertEquals(
            days("2026-03-08", "2026-03-15"),
            range(CalendarView.WEEK, "2026-03-11", DayOfWeek.SUNDAY)
        )
        assertEquals(
            days("2026-03-07", "2026-03-14"),
            range(CalendarView.WEEK, "2026-03-11", DayOfWeek.SATURDAY)
        )
    }

    @Test
    fun `the first day of the week itself starts its own week`() {
        assertEquals(
            days("2026-03-09", "2026-03-16"),
            range(CalendarView.WEEK, "2026-03-09", DayOfWeek.MONDAY)
        )
        // A Sunday belongs to the week that began the Sunday before when weeks start Monday.
        assertEquals(
            days("2026-03-09", "2026-03-16"),
            range(CalendarView.WEEK, "2026-03-15", DayOfWeek.MONDAY)
        )
        assertEquals(
            days("2026-03-15", "2026-03-22"),
            range(CalendarView.WEEK, "2026-03-15", DayOfWeek.SUNDAY)
        )
    }

    @Test
    fun `a week can cross a month and a year`() {
        assertEquals(
            days("2025-12-29", "2026-01-05"),
            range(CalendarView.WEEK, "2026-01-01", DayOfWeek.MONDAY)
        )
    }

    @Test
    fun `a month runs from the first to the first of the next`() {
        assertEquals(days("2026-03-01", "2026-04-01"), range(CalendarView.MONTH, "2026-03-31"))
        assertEquals(days("2026-12-01", "2027-01-01"), range(CalendarView.MONTH, "2026-12-15"))
    }

    @Test
    fun `february has 28 days, or 29 in a leap year`() {
        val common = range(CalendarView.MONTH, "2026-02-10")
        val leap = range(CalendarView.MONTH, "2028-02-10")
        assertEquals(days("2026-02-01", "2026-03-01"), common)
        assertEquals(days("2028-02-01", "2028-03-01"), leap)
        assertTrue(LocalDate.parse("2028-02-29") in leap)
        assertFalse(LocalDate.parse("2026-03-01") in common)
    }

    @Test
    fun `the agenda lists a month onward from the anchor`() {
        assertEquals(days("2026-01-31", "2026-02-28"), range(CalendarView.AGENDA, "2026-01-31"))
        assertEquals(days("2028-01-31", "2028-02-29"), range(CalendarView.AGENDA, "2028-01-31"))
    }

    @Test
    fun `shifting moves by the length of the view`() {
        val date = LocalDate.parse("2026-03-11")
        assertEquals(
            LocalDate.parse("2026-03-12"),
            ViewPeriods.shift(CalendarView.DAY, date, 1)
        )
        assertEquals(
            LocalDate.parse("2026-03-08"),
            ViewPeriods.shift(CalendarView.THREE_DAYS, date, -1)
        )
        assertEquals(
            LocalDate.parse("2026-03-25"),
            ViewPeriods.shift(CalendarView.WEEK, date, 2)
        )
        assertEquals(
            LocalDate.parse("2026-04-11"),
            ViewPeriods.shift(CalendarView.MONTH, date, 1)
        )
        assertEquals(
            LocalDate.parse("2026-02-11"),
            ViewPeriods.shift(CalendarView.AGENDA, date, -1)
        )
        assertEquals(date, ViewPeriods.shift(CalendarView.MONTH, date, 0))
    }

    @Test
    fun `shifting a month clamps to the shorter month and crosses years`() {
        assertEquals(
            LocalDate.parse("2026-02-28"),
            ViewPeriods.shift(CalendarView.MONTH, LocalDate.parse("2026-01-31"), 1)
        )
        assertEquals(
            LocalDate.parse("2028-02-29"),
            ViewPeriods.shift(CalendarView.MONTH, LocalDate.parse("2028-01-31"), 1)
        )
        assertEquals(
            LocalDate.parse("2025-12-31"),
            ViewPeriods.shift(CalendarView.MONTH, LocalDate.parse("2026-01-31"), -1)
        )
    }

    @Test
    fun `a week that has the spring clock change lasts 167 hours`() {
        val week = range(CalendarView.WEEK, "2026-03-29", DayOfWeek.MONDAY)
        assertEquals(days("2026-03-23", "2026-03-30"), week)
        val time = week.toTimeRange(madrid)
        assertEquals(Instant.parse("2026-03-22T23:00:00Z"), time.start)
        assertEquals(Instant.parse("2026-03-29T22:00:00Z"), time.end)
        assertEquals(Duration.ofHours(167), Duration.between(time.start, time.end))
    }

    @Test
    fun `a week that has the autumn clock change lasts 169 hours`() {
        val week = range(CalendarView.WEEK, "2026-10-25", DayOfWeek.MONDAY)
        val time = week.toTimeRange(madrid)
        assertEquals(Duration.ofHours(169), Duration.between(time.start, time.end))
    }

    @Test
    fun `the day of a clock change is 23 or 25 hours long`() {
        val spring = range(CalendarView.DAY, "2026-03-29").toTimeRange(madrid)
        val autumn = range(CalendarView.DAY, "2026-10-25").toTimeRange(madrid)
        assertEquals(Duration.ofHours(23), Duration.between(spring.start, spring.end))
        assertEquals(Duration.ofHours(25), Duration.between(autumn.start, autumn.end))
    }

    @Test
    fun `a range contains its first day and not its end`() {
        val week = range(CalendarView.WEEK, "2026-03-11")
        assertTrue(LocalDate.parse("2026-03-09") in week)
        assertTrue(LocalDate.parse("2026-03-15") in week)
        assertFalse(LocalDate.parse("2026-03-08") in week)
        assertFalse(LocalDate.parse("2026-03-16") in week)
    }

    @Test
    fun `a range must cover at least a day`() {
        val day = LocalDate.parse("2026-03-11")
        assertThrows(IllegalArgumentException::class.java) { DateRange(day, day) }
    }
}
