// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.widget

import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MonthWidgetsTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val today = LocalDate.parse("2026-10-06")
    private val october = YearMonth.of(2026, 10)
    private val calendarColor = 0xFF112233.toInt()
    private val colors = mapOf(CalendarId(1) to calendarColor)

    private fun at(
        day: String,
        hour: Int,
        id: Long,
        status: AttendeeStatus? = null,
        color: Int? = null
    ): EventInstance {
        val start = LocalDate.parse(day).atTime(hour, 0).atZone(zone).toInstant()
        return EventInstance(
            EventId(id),
            CalendarId(1),
            "E$id",
            EventTime.Timed(start, start.plusSeconds(3600), zone),
            color = color,
            selfStatus = status
        )
    }

    private fun build(
        instances: List<EventInstance>,
        markers: Int = 3,
        first: DayOfWeek = DayOfWeek.MONDAY
    ) = MonthWidgets.build(october, today, first, zone, instances, colors, markers)

    private fun cell(model: MonthWidgetModel, day: String) =
        model.weeks.flatten().first { it.date == LocalDate.parse(day) }

    @Test
    fun `the grid is the month's whole weeks from the first day of the week`() {
        val model = build(emptyList())

        assertEquals(5, model.weeks.size)
        assertTrue(model.weeks.all { it.size == 7 })
        assertEquals(LocalDate.parse("2026-09-28"), model.weeks.first().first().date)
        assertEquals(LocalDate.parse("2026-11-01"), model.weeks.last().last().date)
        assertEquals(DayOfWeek.MONDAY, model.weekdays.first())
        assertEquals(DayOfWeek.SUNDAY, model.weekdays.last())
        assertEquals(october, model.month)
        assertEquals(
            DayOfWeek.SUNDAY,
            build(emptyList(), first = DayOfWeek.SUNDAY).weekdays.first()
        )
    }

    @Test
    fun `today is marked and neighbouring months are not part of the month`() {
        val model = build(emptyList())

        assertTrue(cell(model, "2026-10-06").isToday)
        assertEquals(1, model.weeks.flatten().count { it.isToday })
        assertFalse(cell(model, "2026-09-30").inMonth)
        assertTrue(cell(model, "2026-10-01").inMonth)
        assertFalse(cell(model, "2026-11-01").inMonth)
    }

    @Test
    fun `a day with events gets a marker per event in the event or calendar color`() {
        val model = build(listOf(at("2026-10-08", 9, 1), at("2026-10-08", 11, 2, color = 5)))
        val day = cell(model, "2026-10-08")

        assertEquals(listOf(calendarColor, 5), day.markers.map { it.color })
        assertEquals(2, day.eventCount)
        assertEquals(0, day.overflow)
        assertEquals(WidgetTap.OpenDay(LocalDate.parse("2026-10-08")), day.tap)
        assertTrue(cell(model, "2026-10-09").markers.isEmpty())
    }

    @Test
    fun `events beyond the markers that fit are counted as overflow`() {
        val events = (1..5).map { at("2026-10-08", 8 + it, it.toLong()) }

        val three = cell(build(events, markers = 3), "2026-10-08")
        val one = cell(build(events, markers = 1), "2026-10-08")

        assertEquals(3, three.markers.size)
        assertEquals(2, three.overflow)
        assertEquals(5, three.eventCount)
        assertEquals(1, one.markers.size)
        assertEquals(4, one.overflow)
    }

    @Test
    fun `pending invitations are outlined markers and flag the day, all-day events lead`() {
        val allDay = EventInstance(
            EventId(9),
            CalendarId(1),
            "Holiday",
            EventTime.AllDay(LocalDate.parse("2026-10-08"), LocalDate.parse("2026-10-09")),
            color = 7
        )
        val model = build(listOf(at("2026-10-08", 9, 1, AttendeeStatus.NEEDS_ACTION), allDay))
        val day = cell(model, "2026-10-08")

        assertEquals(listOf(7, calendarColor), day.markers.map { it.color })
        assertEquals(listOf(false, true), day.markers.map { it.pending })
        assertTrue(day.hasPending)
        assertFalse(cell(model, "2026-10-09").hasPending)
    }

    @Test
    fun `a multi-day event marks every day it touches`() {
        val trip = EventInstance(
            EventId(3),
            CalendarId(1),
            "Trip",
            EventTime.AllDay(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-15"))
        )
        val model = build(listOf(trip))

        listOf("2026-10-12", "2026-10-13", "2026-10-14").forEach {
            assertEquals(1, cell(model, it).eventCount, it)
        }
        assertEquals(0, cell(model, "2026-10-15").eventCount)
    }

    @Test
    fun `the day of a clock change has its events on it`() {
        // 2026-10-25: Madrid goes back an hour; events at 00:00 and 23:00 are both that day.
        val model = build(listOf(at("2026-10-25", 0, 1), at("2026-10-25", 23, 2)))

        assertEquals(2, cell(model, "2026-10-25").eventCount)
        assertEquals(0, cell(model, "2026-10-24").eventCount)
        assertEquals(0, cell(model, "2026-10-26").eventCount)
    }

    @Test
    fun `the range read is the whole grid`() {
        val range = MonthWidgets.range(october, DayOfWeek.MONDAY)

        assertEquals(LocalDate.parse("2026-09-28"), range.start)
        assertEquals(LocalDate.parse("2026-11-02"), range.endExclusive)
    }

    @Test
    fun `the month shown moves by the offset and is clamped`() {
        assertEquals(october, MonthWidgets.shown(today, 0))
        assertEquals(YearMonth.of(2026, 9), MonthWidgets.shown(today, -1))
        assertEquals(YearMonth.of(2027, 1), MonthWidgets.shown(today, 3))
        assertEquals(YearMonth.of(2036, 10), MonthWidgets.shown(today, 100_000))
        assertEquals(YearMonth.of(2016, 10), MonthWidgets.shown(today, -100_000))
        assertEquals(-120, MonthWidgets.clampOffset(-121))
        assertEquals(7, MonthWidgets.clampOffset(7))
    }

    @Test
    fun `taller cells hold more markers, from one to three`() {
        assertEquals(1, MonthWidgets.markerCount(0))
        assertEquals(1, MonthWidgets.markerCount(39))
        assertEquals(2, MonthWidgets.markerCount(40))
        assertEquals(2, MonthWidgets.markerCount(51))
        assertEquals(3, MonthWidgets.markerCount(52))
        assertEquals(3, MonthWidgets.markerCount(400))
    }

    @Test
    fun `a cell shows at least one marker`() {
        assertThrows<IllegalArgumentException> { build(emptyList(), markers = 0) }
    }
}
