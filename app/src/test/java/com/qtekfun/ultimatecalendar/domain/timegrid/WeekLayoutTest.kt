// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import com.qtekfun.ultimatecalendar.domain.navigation.ViewPeriods
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** The 7-day pages of the Week view (RF-03, T16): boundaries, DST weeks and multi-day events. */
class WeekLayoutTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private var lastId = 0L

    private fun timed(from: LocalDateTime, to: LocalDateTime) = EventInstance(
        eventId = EventId(++lastId),
        calendarId = CalendarId(1),
        title = "Event $lastId",
        time = EventTime.Timed(
            from.atZone(madrid).toInstant(),
            to.atZone(madrid).toInstant(),
            madrid
        )
    )

    private fun allDay(from: LocalDate, to: LocalDate) = EventInstance(
        eventId = EventId(++lastId),
        calendarId = CalendarId(1),
        title = "All day $lastId",
        time = EventTime.AllDay(from, to)
    )

    private fun weekOf(date: String, first: DayOfWeek = DayOfWeek.MONDAY) =
        ViewPeriods.range(CalendarView.WEEK, LocalDate.parse(date), first)

    private fun build(range: DateRange, vararg events: EventInstance) =
        TimeGridLayout.build(range, madrid, events.toList())

    @ParameterizedTest
    @CsvSource("MONDAY,2026-03-09", "SUNDAY,2026-03-08", "SATURDAY,2026-03-07")
    fun `the week starts on the first day of the week and has seven columns`(
        first: DayOfWeek,
        start: String
    ) {
        val page = build(weekOf("2026-03-11", first))

        assertEquals(7, page.days.size)
        assertEquals(LocalDate.parse(start), page.days.first())
        assertEquals(first, page.days.first().dayOfWeek)
        assertEquals(LocalDate.parse(start).plusDays(6), page.days.last())
    }

    @ParameterizedTest
    @CsvSource("MONDAY,0", "SUNDAY,1", "SATURDAY,2")
    fun `a Wednesday event lands in the column of its weekday`(first: DayOfWeek, offset: Int) {
        val wednesday = LocalDate.parse("2026-03-11")
        val page = build(
            weekOf("2026-03-11", first),
            timed(wednesday.atTime(9, 0), wednesday.atTime(10, 0))
        )

        // Wednesday is the 3rd day for Monday, 4th for Sunday and 5th for Saturday starts.
        assertEquals(2 + offset, page.timed.single().dayIndex)
        assertEquals(wednesday, page.days[page.timed.single().dayIndex])
    }

    @Test
    fun `a Sunday event is the last column of a Monday week and the first of the next`() {
        val sunday = LocalDate.parse("2026-03-15")
        val event = timed(sunday.atTime(10, 0), sunday.atTime(11, 0))

        assertEquals(6, build(weekOf("2026-03-11"), event).timed.single().dayIndex)
        assertTrue(build(weekOf("2026-03-16"), event).timed.isEmpty())
        assertEquals(
            0,
            build(weekOf("2026-03-15", DayOfWeek.SUNDAY), event).timed.single().dayIndex
        )
    }

    @Test
    fun `the week of the spring change lasts 167 hours and its days still have 24 grid hours`() {
        val range = weekOf("2026-03-25")
        val hours = Duration.between(
            range.start.atStartOfDay(madrid),
            range.endExclusive.atStartOfDay(madrid)
        ).toHours()
        assertEquals(167, hours)

        // Sunday 29 March: 02:00 does not exist. 01:30 to 03:30 (one hour long) is drawn on the
        // wall clock, from minute 90 to 210, in the Sunday column.
        val sunday = LocalDate.parse("2026-03-29")
        val page = build(range, timed(sunday.atTime(1, 30), sunday.atTime(3, 30)))
        val block = page.timed.single()
        assertEquals(7, page.days.size)
        assertEquals(6, block.dayIndex)
        assertEquals(90, block.startMinute)
        assertEquals(210, block.endMinute)
    }

    @Test
    fun `the week of the autumn change lasts 169 hours and an event across it keeps its columns`() {
        val range = weekOf("2026-10-21")
        val hours = Duration.between(
            range.start.atStartOfDay(madrid),
            range.endExclusive.atStartOfDay(madrid)
        ).toHours()
        assertEquals(169, hours)

        // Saturday 24 22:00 to Sunday 25 04:00 spans the repeated hour: 7 hours of real time.
        val from = LocalDate.parse("2026-10-24").atTime(22, 0)
        val to = LocalDate.parse("2026-10-25").atTime(4, 0)
        val page = build(range, timed(from, to))
        assertEquals(listOf(5, 6), page.timed.map { it.dayIndex })
        assertEquals(22 * 60, page.timed[0].startMinute)
        assertEquals(TimeScale.MINUTES_PER_DAY, page.timed[0].endMinute)
        assertEquals(0, page.timed[1].startMinute)
        assertEquals(4 * 60, page.timed[1].endMinute)
    }

    @Test
    fun `a timed event over several days has a block on each day with the right continuations`() {
        val friday = LocalDate.parse("2026-03-13")
        val page = build(
            weekOf("2026-03-11"),
            timed(friday.atTime(22, 0), friday.plusDays(2).atTime(2, 0))
        )

        assertEquals(listOf(4, 5, 6), page.timed.map { it.dayIndex })
        assertEquals(listOf(22 * 60, 0, 0), page.timed.map { it.startMinute })
        assertEquals(listOf(1440, 1440, 120), page.timed.map { it.endMinute })
        assertEquals(listOf(false, true, true), page.timed.map { it.continuesBefore })
        assertEquals(listOf(true, true, false), page.timed.map { it.continuesAfter })
    }

    @Test
    fun `an event that ends at midnight does not touch the next day`() {
        val saturday = LocalDate.parse("2026-03-14")
        val page =
            build(
                weekOf("2026-03-11"),
                timed(saturday.atTime(23, 0), saturday.plusDays(1).atTime(0, 0))
            )

        val block = page.timed.single()
        assertEquals(5, block.dayIndex)
        assertEquals(1440, block.endMinute)
        assertFalse(block.continuesAfter)
    }

    @Test
    fun `an event crossing the end of the week continues into the next page`() {
        val sunday = LocalDate.parse("2026-03-15")
        val event = timed(sunday.atTime(23, 0), sunday.plusDays(1).atTime(1, 0))

        val thisWeek = build(weekOf("2026-03-11"), event).timed.single()
        val nextWeek = build(weekOf("2026-03-16"), event).timed.single()

        assertTrue(thisWeek.continuesAfter)
        assertEquals(6, thisWeek.dayIndex)
        assertTrue(nextWeek.continuesBefore)
        assertEquals(0, nextWeek.dayIndex)
        assertEquals(60, nextWeek.endMinute)
    }

    @Test
    fun `an all-day event spanning the week boundary is clipped and marked as continuing`() {
        val range = weekOf("2026-03-11")
        val trip = allDay(LocalDate.parse("2026-03-05"), LocalDate.parse("2026-03-12"))
        val later = allDay(LocalDate.parse("2026-03-14"), LocalDate.parse("2026-03-19"))

        val bars = build(range, trip, later).allDay.associateBy { it.instance.eventId }

        val first = bars.getValue(trip.eventId)
        assertEquals(0, first.firstDay)
        assertEquals(2, first.lastDay)
        assertTrue(first.continuesBefore)
        assertFalse(first.continuesAfter)
        val second = bars.getValue(later.eventId)
        assertEquals(5, second.firstDay)
        assertEquals(6, second.lastDay)
        assertTrue(second.continuesAfter)
    }

    @Test
    fun `overlapping all-day events take different rows and longer ones go first`() {
        val monday = LocalDate.parse("2026-03-09")
        val short = allDay(monday.plusDays(1), monday.plusDays(2))
        val long = allDay(monday.plusDays(1), monday.plusDays(5))
        val apart = allDay(monday.plusDays(5), monday.plusDays(6))

        val page = build(weekOf("2026-03-11"), short, long, apart)
        val rows = page.allDay.associate { it.instance.eventId to it.row }

        assertEquals(0, rows.getValue(long.eventId))
        assertEquals(1, rows.getValue(short.eventId))
        assertEquals(0, rows.getValue(apart.eventId))
        assertEquals(2, page.allDayRows)
    }
}
