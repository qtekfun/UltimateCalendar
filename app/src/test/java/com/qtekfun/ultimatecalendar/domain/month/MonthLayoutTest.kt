// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.month

import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MonthLayoutTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    // October 2026 with Monday weeks: 28 Sep - 4 Oct, 5 - 11 Oct, ... five rows.
    private val grid = MonthGrid.of(YearMonth.parse("2026-10"), DayOfWeek.MONDAY)
    private var lastId = 0L

    private fun date(text: String) = LocalDate.parse("2026-$text")

    private fun timed(
        from: String,
        to: String,
        calendar: Long = 1,
        color: Int? = null,
        status: AttendeeStatus? = null,
        title: String = "Event ${lastId + 1}"
    ) = EventInstance(
        eventId = EventId(++lastId),
        calendarId = CalendarId(calendar),
        title = title,
        time = EventTime.Timed(
            LocalDateTime.parse("2026-$from").atZone(madrid).toInstant(),
            LocalDateTime.parse("2026-$to").atZone(madrid).toInstant(),
            madrid
        ),
        color = color,
        selfStatus = status
    )

    private fun allDay(
        from: String,
        to: String,
        title: String = "All day ${lastId + 1}",
        color: Int? = null
    ) = EventInstance(
        eventId = EventId(++lastId),
        calendarId = CalendarId(1),
        title = title,
        time = EventTime.AllDay(date(from), date(to)),
        color = color
    )

    private fun build(vararg events: EventInstance, colors: Map<CalendarId, Int> = emptyMap()) =
        MonthLayout.build(grid, madrid, events.toList(), colors)

    private fun MonthWeek.titles() = bars.map { it.instance.title }

    @Test
    fun `a month with nothing has its rows empty`() {
        val page = build()

        assertEquals(5, page.weeks.size)
        assertTrue(page.weeks.all { it.bars.isEmpty() })
        assertEquals(grid.weeks, page.weeks.map { it.days })
    }

    @Test
    fun `a timed event is a one-day dot on its day`() {
        val bar = build(timed("10-07T09:00", "10-07T10:00")).weeks[1].bars.single()

        assertEquals(MonthBarStyle.TIMED, bar.style)
        assertEquals(2, bar.firstCol)
        assertEquals(2, bar.lastCol)
        assertEquals(0, bar.lane)
        assertFalse(bar.continuesBefore || bar.continuesAfter)
    }

    @Test
    fun `an all-day event is a bar over its days`() {
        val bar = build(allDay("10-06", "10-09")).weeks[1].bars.single()

        assertEquals(MonthBarStyle.BAR, bar.style)
        assertEquals(1, bar.firstCol)
        assertEquals(3, bar.lastCol)
    }

    @Test
    fun `a bar that crosses a row is cut and marked as continuing`() {
        // 3 to 6 October inclusive: Saturday and Sunday of the first row, then Monday, Tuesday.
        val page = build(allDay("10-03", "10-07"))

        val first = page.weeks[0].bars.single()
        val second = page.weeks[1].bars.single()
        assertEquals(5..6, first.firstCol..first.lastCol)
        assertTrue(first.continuesAfter)
        assertFalse(first.continuesBefore)
        assertEquals(0..1, second.firstCol..second.lastCol)
        assertTrue(second.continuesBefore)
        assertFalse(second.continuesAfter)
    }

    @Test
    fun `an event over three rows has a middle row that continues both ways`() {
        val page = build(allDay("10-02", "10-19"))

        val middle = page.weeks[1].bars.single()
        assertEquals(0..6, middle.firstCol..middle.lastCol)
        assertTrue(middle.continuesBefore && middle.continuesAfter)
        assertEquals(3, page.weeks.count { it.bars.isNotEmpty() })
    }

    @Test
    fun `a bar from the previous month starts before the grid and is cut at its edge`() {
        val bar = build(allDay("09-20", "10-01")).weeks[0].bars.single()

        assertEquals(0..2, bar.firstCol..bar.lastCol)
        assertTrue(bar.continuesBefore)
        assertFalse(bar.continuesAfter)
    }

    @Test
    fun `an event outside the grid is ignored`() {
        val page = build(allDay("12-01", "12-03"), timed("08-01T09:00", "08-01T10:00"))

        assertTrue(page.weeks.all { it.bars.isEmpty() })
    }

    @Test
    fun `an event over midnight is a bar over both days`() {
        val bar = build(timed("10-07T22:00", "10-08T02:00")).weeks[1].bars.single()

        assertEquals(MonthBarStyle.BAR, bar.style)
        assertEquals(2..3, bar.firstCol..bar.lastCol)
    }

    @Test
    fun `an event that ends at midnight sharp stays on its day`() {
        val bar = build(timed("10-07T20:00", "10-08T00:00")).weeks[1].bars.single()

        assertEquals(MonthBarStyle.TIMED, bar.style)
        assertEquals(2..2, bar.firstCol..bar.lastCol)
    }

    @Test
    fun `an event of no length takes its day`() {
        val bar = build(timed("10-07T12:00", "10-07T12:00")).weeks[1].bars.single()

        assertEquals(2..2, bar.firstCol..bar.lastCol)
    }

    @Test
    fun `days are the device's days, wherever the event started`() {
        // 23:30 in Madrid is already the next day in Tokyo, but the grid is the device's.
        val tokyo = ZoneId.of("Asia/Tokyo")
        val instance = EventInstance(
            EventId(99),
            CalendarId(1),
            "Late",
            EventTime.Timed(
                LocalDateTime.parse("2026-10-07T23:30").atZone(madrid).toInstant(),
                LocalDateTime.parse("2026-10-07T23:45").atZone(madrid).toInstant(),
                tokyo
            )
        )

        val bar = MonthLayout.build(grid, madrid, listOf(instance)).weeks[1].bars.single()

        assertEquals(2..2, bar.firstCol..bar.lastCol)
    }

    @Test
    fun `overlapping bars take different lanes and separate ones share a lane`() {
        val week = build(
            allDay("10-05", "10-08"),
            allDay("10-06", "10-09"),
            allDay("10-09", "10-11")
        ).weeks[1]

        assertEquals(listOf(0, 1, 0), week.bars.map { it.lane })
    }

    @Test
    fun `longer bars come first when they start on the same day`() {
        val week = build(
            allDay("10-05", "10-06", title = "Short"),
            allDay("10-05", "10-09", title = "Long")
        ).weeks[1]

        assertEquals(listOf("Long", "Short"), week.titles())
        assertEquals(listOf(0, 1), week.bars.map { it.lane })
    }

    @Test
    fun `all-day events are above the timed ones of their day`() {
        val week = build(
            timed("10-07T08:00", "10-07T09:00", title = "Early"),
            allDay("10-07", "10-08", title = "Holiday"),
            timed("10-07T18:00", "10-07T19:00", title = "Late")
        ).weeks[1]

        assertEquals(listOf("Holiday", "Early", "Late"), week.barsOn(2).map { it.instance.title })
        assertEquals(listOf(0, 1, 2), week.barsOn(2).map { it.lane })
    }

    @Test
    fun `timed events of a day go in the order they start`() {
        val week = build(
            timed("10-07T15:00", "10-07T16:00", title = "Afternoon"),
            timed("10-07T08:00", "10-07T09:00", title = "Morning")
        ).weeks[1]

        assertEquals(listOf("Morning", "Afternoon"), week.barsOn(2).map { it.instance.title })
    }

    @Test
    fun `a timed event takes the lane after the bars crossing its day`() {
        val week = build(
            allDay("10-06", "10-09", title = "Trip"),
            timed("10-08T10:00", "10-08T11:00", title = "Meeting")
        ).weeks[1]

        assertEquals(1, week.barsOn(3).first { it.instance.title == "Meeting" }.lane)
    }

    @Test
    fun `a color of the event wins over the one of its calendar`() {
        val page = build(
            timed("10-07T09:00", "10-07T10:00", calendar = 1, color = 0xFF111111.toInt()),
            timed("10-07T10:00", "10-07T11:00", calendar = 2),
            timed("10-07T11:00", "10-07T12:00", calendar = 3),
            colors = mapOf(CalendarId(1) to 0xFF222222.toInt(), CalendarId(2) to 0xFF333333.toInt())
        )

        assertEquals(
            listOf(0xFF111111.toInt(), 0xFF333333.toInt(), null),
            page.weeks[1].bars.map { it.color }
        )
    }

    @Test
    fun `an unanswered invitation is pending and an accepted one is not`() {
        val week = build(
            timed("10-07T09:00", "10-07T10:00", status = AttendeeStatus.NEEDS_ACTION),
            timed("10-07T10:00", "10-07T11:00", status = AttendeeStatus.ACCEPTED),
            timed("10-07T11:00", "10-07T12:00")
        ).weeks[1]

        assertEquals(listOf(true, false, false), week.bars.map { it.isPending })
    }

    @Test
    fun `on the day of a clock change events stay on their own day`() {
        // Madrid went from 02:00 to 03:00 on Sunday 29 March 2026.
        val march = MonthGrid.of(YearMonth.parse("2026-03"), DayOfWeek.MONDAY)
        val sunday = timed("03-29T01:30", "03-29T03:30", title = "Across the change")
        val saturdayNight = timed("03-28T23:30", "03-29T03:30", title = "Night shift")

        val week = MonthLayout.build(march, madrid, listOf(sunday, saturdayNight)).weeks[4]

        assertEquals(LocalDate.parse("2026-03-29"), week.days[6])
        val across = week.bars.single { it.instance.title == "Across the change" }
        assertEquals(6..6, across.firstCol..across.lastCol)
        val night = week.bars.single { it.instance.title == "Night shift" }
        assertEquals(5..6, night.firstCol..night.lastCol)
    }

    @Test
    fun `barsOn lists what touches a column by lane`() {
        val week = build(
            allDay("10-05", "10-07", title = "A"),
            allDay("10-06", "10-08", title = "B")
        ).weeks[1]

        assertEquals(listOf("A"), week.barsOn(0).map { it.instance.title })
        assertEquals(listOf("A", "B"), week.barsOn(1).map { it.instance.title })
        assertEquals(listOf("B"), week.barsOn(2).map { it.instance.title })
        assertTrue(week.barsOn(4).isEmpty())
    }
}
