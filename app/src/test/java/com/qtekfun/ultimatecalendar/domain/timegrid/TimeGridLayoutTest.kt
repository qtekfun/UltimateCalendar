// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
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

class TimeGridLayoutTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val day = LocalDate.parse("2026-03-11")
    private val one = DateRange(day, day.plusDays(1))
    private val three = DateRange(day, day.plusDays(3))
    private var lastId = 0L

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(hour, minute)

    private fun timed(
        from: LocalDateTime,
        to: LocalDateTime,
        zone: ZoneId = madrid,
        eventZone: ZoneId = zone,
        color: Int? = null,
        calendar: Long = 1,
        status: AttendeeStatus? = null
    ) = EventInstance(
        eventId = EventId(++lastId),
        calendarId = CalendarId(calendar),
        title = "Event $lastId",
        time = EventTime.Timed(
            from.atZone(zone).toInstant(),
            to.atZone(zone).toInstant(),
            eventZone
        ),
        color = color,
        selfStatus = status
    )

    private fun allDay(from: LocalDate, to: LocalDate, color: Int? = null) = EventInstance(
        eventId = EventId(++lastId),
        calendarId = CalendarId(1),
        title = "All day $lastId",
        time = EventTime.AllDay(from, to),
        color = color
    )

    private fun build(
        range: DateRange,
        vararg events: EventInstance,
        colors: Map<CalendarId, Int> = emptyMap()
    ) = TimeGridLayout.build(range, madrid, events.toList(), colors)

    @Test
    fun `an event is placed at its wall-clock minutes`() {
        val page = build(one, timed(at(day, 9, 15), at(day, 10, 45)))

        val block = page.timed.single()
        assertEquals(9 * 60 + 15, block.startMinute)
        assertEquals(10 * 60 + 45, block.endMinute)
        assertEquals(0, block.dayIndex)
        assertFalse(block.continuesBefore)
        assertFalse(block.continuesAfter)
    }

    @Test
    fun `days of the range become the columns`() {
        val page = build(three)

        assertEquals(listOf(day, day.plusDays(1), day.plusDays(2)), page.days)
    }

    @Test
    fun `overlapping events share the width`() {
        val page = build(
            one,
            timed(at(day, 9), at(day, 11)),
            timed(at(day, 10), at(day, 12))
        )

        assertEquals(listOf(0, 1), page.timed.map { it.column })
        assertEquals(listOf(2, 2), page.timed.map { it.columns })
    }

    @Test
    fun `events on different days do not share columns`() {
        val next = day.plusDays(1)
        val page = build(three, timed(at(day, 9), at(day, 10)), timed(at(next, 9), at(next, 10)))

        assertEquals(listOf(1, 1), page.timed.map { it.columns })
        assertEquals(listOf(0, 1), page.timed.map { it.dayIndex })
    }

    @Test
    fun `touching events stay in one column`() {
        val page = build(one, timed(at(day, 9), at(day, 10)), timed(at(day, 10), at(day, 11)))

        assertEquals(listOf(1, 1), page.timed.map { it.columns })
    }

    @Test
    fun `a zero-length event is kept, with room of the minimum duration`() {
        val page = build(
            one,
            timed(at(day, 9), at(day, 9)),
            timed(at(day, 9, 15), at(day, 10))
        )

        assertEquals(2, page.timed.size)
        assertEquals(listOf(2, 2), page.timed.map { it.columns })
        assertEquals(9 * 60, page.timed.first().endMinute)
    }

    @Test
    fun `a zero-length event at midnight belongs to the day it starts`() {
        val next = day.plusDays(1)
        val page = build(three, timed(at(next, 0), at(next, 0)))

        val block = page.timed.single()
        assertEquals(1, block.dayIndex)
        assertEquals(0, block.startMinute)
        assertEquals(0, block.endMinute)
    }

    @Test
    fun `an event that ends at midnight stays on its day`() {
        val next = day.plusDays(1)
        val page = build(three, timed(at(day, 22), at(next, 0)))

        val block = page.timed.single()
        assertEquals(0, block.dayIndex)
        assertEquals(1440, block.endMinute)
        assertFalse(block.continuesAfter)
    }

    @Test
    fun `an event crossing midnight is cut at each day`() {
        val next = day.plusDays(1)
        val page = build(three, timed(at(day, 22), at(next, 2)))

        assertEquals(2, page.timed.size)
        val (first, second) = page.timed
        assertEquals(
            Triple(0, 22 * 60, 1440),
            Triple(first.dayIndex, first.startMinute, first.endMinute)
        )
        assertTrue(first.continuesAfter)
        assertFalse(first.continuesBefore)
        assertEquals(
            Triple(1, 0, 2 * 60),
            Triple(second.dayIndex, second.startMinute, second.endMinute)
        )
        assertTrue(second.continuesBefore)
        assertFalse(second.continuesAfter)
    }

    @Test
    fun `a timed event over several days fills the days between`() {
        val page = build(three, timed(at(day.minusDays(1), 20), at(day.plusDays(3), 8)))

        assertEquals(listOf(0, 1, 2), page.timed.map { it.dayIndex })
        assertEquals(listOf(0, 0, 0), page.timed.map { it.startMinute })
        assertEquals(listOf(1440, 1440, 1440), page.timed.map { it.endMinute })
        assertTrue(page.timed.all { it.continuesBefore && it.continuesAfter })
    }

    @Test
    fun `events outside the range are left out`() {
        val before = timed(at(day.minusDays(1), 9), at(day.minusDays(1), 10))
        val after = timed(at(day.plusDays(1), 9), at(day.plusDays(1), 10))

        assertTrue(build(one, before, after).timed.isEmpty())
    }

    @Test
    fun `an event is placed by the device's wall clock, whatever its own zone`() {
        // 09:00 in Tokyo is 01:00 in Madrid (UTC+1 on 11 March 2026).
        val event = timed(at(day, 9), at(day, 10), zone = tokyo)

        val block = build(one, event).timed.single()

        assertEquals(60, block.startMinute)
        assertEquals(2 * 60, block.endMinute)
    }

    @Test
    fun `an event in another zone is flagged, one in the same offset is not`() {
        val abroad = timed(at(day, 9), at(day, 10), zone = tokyo)
        val sameClock =
            timed(at(day, 9), at(day, 10), zone = madrid, eventZone = ZoneId.of("Europe/Paris"))
        val home = timed(at(day, 9), at(day, 10))

        val page = build(one, abroad, sameClock, home)

        val flagged = page.timed.associate { it.instance.eventId to it.otherZone }
        assertEquals(tokyo, flagged.getValue(abroad.eventId))
        assertNull(flagged.getValue(sameClock.eventId))
        assertNull(flagged.getValue(home.eventId))
    }

    @Test
    fun `the offset is compared on the day of the event, not today`() {
        // Lagos is UTC+1 all year; Madrid is UTC+1 in winter and UTC+2 in summer.
        val lagos = ZoneId.of("Africa/Lagos")
        val winter = LocalDate.parse("2026-01-14")
        val summer = LocalDate.parse("2026-07-14")
        val inWinter = timed(at(winter, 9), at(winter, 10), eventZone = lagos)
        val inSummer = timed(at(summer, 9), at(summer, 10), eventZone = lagos)

        val flagged = listOf(winter, summer).map { date ->
            TimeGridLayout.build(
                DateRange(date, date.plusDays(1)),
                madrid,
                listOf(inWinter, inSummer)
            ).timed.single().otherZone
        }

        assertEquals(listOf(null, lagos), flagged)
    }

    @Test
    fun `an unanswered invitation is pending, an answered one is not`() {
        val open = timed(at(day, 9), at(day, 10), status = AttendeeStatus.NEEDS_ACTION)
        val accepted = timed(at(day, 11), at(day, 12), status = AttendeeStatus.ACCEPTED)
        val own = timed(at(day, 13), at(day, 14))

        val page = build(one, open, accepted, own)

        assertEquals(listOf(true, false, false), page.timed.map { it.isPending })
    }

    @Test
    fun `the event's color wins over its calendar's`() {
        val colors = mapOf(CalendarId(1) to 0xFF112233.toInt(), CalendarId(2) to 0xFF445566.toInt())
        val own = timed(at(day, 9), at(day, 10), color = 0xFFAABBCC.toInt())
        val inherited = timed(at(day, 11), at(day, 12), calendar = 2)
        val unknown = timed(at(day, 13), at(day, 14), calendar = 3)

        val page = build(one, own, inherited, unknown, colors = colors)

        assertEquals(
            listOf(0xFFAABBCC.toInt(), 0xFF445566.toInt(), null),
            page.timed.map {
                it.color
            }
        )
    }

    @Test
    fun `all-day events go to the strip, not the grid`() {
        val page = build(three, allDay(day, day.plusDays(1)))

        assertTrue(page.timed.isEmpty())
        val bar = page.allDay.single()
        assertEquals(0 to 0, bar.firstDay to bar.lastDay)
        assertEquals(1, page.allDayRows)
    }

    @Test
    fun `a multi-day all-day event spans its columns and is cut at the range`() {
        val inside = allDay(day.plusDays(1), day.plusDays(3))
        val across = allDay(day.minusDays(2), day.plusDays(10))

        val page = build(three, inside, across)

        val bars = page.allDay.associateBy { it.instance.eventId }
        assertEquals(1 to 2, bars.getValue(inside.eventId).let { it.firstDay to it.lastDay })
        assertFalse(bars.getValue(inside.eventId).continuesBefore)
        assertFalse(bars.getValue(inside.eventId).continuesAfter)
        val long = bars.getValue(across.eventId)
        assertEquals(0 to 2, long.firstDay to long.lastDay)
        assertTrue(long.continuesBefore)
        assertTrue(long.continuesAfter)
    }

    @Test
    fun `overlapping all-day events take separate rows`() {
        val page = build(
            three,
            allDay(day, day.plusDays(2)),
            allDay(day.plusDays(1), day.plusDays(2)),
            allDay(day.plusDays(2), day.plusDays(3))
        )

        assertEquals(listOf(0, 1, 0), page.allDay.map { it.row })
        assertEquals(2, page.allDayRows)
    }

    @Test
    fun `all-day events outside the range are left out and an empty strip has no rows`() {
        val page = build(
            one,
            allDay(day.minusDays(3), day),
            allDay(day.plusDays(1), day.plusDays(2))
        )

        assertTrue(page.allDay.isEmpty())
        assertEquals(0, page.allDayRows)
    }

    @Test
    fun `an unanswered all-day invitation is pending and takes the calendar color`() {
        val event = allDay(day, day.plusDays(1)).copy(selfStatus = AttendeeStatus.NEEDS_ACTION)

        val bar = build(one, event, colors = mapOf(CalendarId(1) to 7)).allDay.single()

        assertTrue(bar.isPending)
        assertEquals(7, bar.color)
    }

    // Clock changes: the grid always has 24 wall-clock hours (see the object's documentation).

    @Test
    fun `on the 23 hour day events sit at their local times`() {
        val change = LocalDate.parse("2026-03-29")
        val page = TimeGridLayout.build(
            DateRange(change, change.plusDays(1)),
            madrid,
            listOf(
                timed(at(change, 0, 30), at(change, 1, 30)),
                timed(at(change, 3), at(change, 4))
            )
        )

        assertEquals(listOf(30, 180), page.timed.map { it.startMinute })
        assertEquals(listOf(90, 240), page.timed.map { it.endMinute })
    }

    @Test
    fun `an event across the spring-forward gap spans the skipped hour`() {
        val change = LocalDate.parse("2026-03-29")
        // 01:30 to 03:30 local is one real hour.
        val event = timed(at(change, 1, 30), at(change, 3, 30))

        val block = TimeGridLayout.build(
            DateRange(change, change.plusDays(1)),
            madrid,
            listOf(event)
        ).timed.single()

        assertEquals(90, block.startMinute)
        assertEquals(210, block.endMinute)
    }

    @Test
    fun `an event ending on the next day after a 23 hour day is cut at the day end`() {
        val change = LocalDate.parse("2026-03-29")
        val page = TimeGridLayout.build(
            DateRange(change, change.plusDays(2)),
            madrid,
            listOf(timed(at(change, 23), at(change.plusDays(1), 1)))
        )

        assertEquals(listOf(23 * 60, 0), page.timed.map { it.startMinute })
        assertEquals(listOf(1440, 60), page.timed.map { it.endMinute })
    }

    @Test
    fun `on the 25 hour day both passes of the repeated hour are drawn overlaid`() {
        val change = LocalDate.parse("2026-10-25")
        val zone = madrid
        val first = EventInstance(
            EventId(100),
            CalendarId(1),
            "first pass",
            EventTime.Timed(
                change.atTime(2, 0).atZone(zone).withEarlierOffsetAtOverlap().toInstant(),
                change.atTime(2, 30).atZone(zone).withEarlierOffsetAtOverlap().toInstant(),
                zone
            )
        )
        val second = EventInstance(
            EventId(101),
            CalendarId(1),
            "second pass",
            EventTime.Timed(
                change.atTime(2, 0).atZone(zone).withLaterOffsetAtOverlap().toInstant(),
                change.atTime(2, 30).atZone(zone).withLaterOffsetAtOverlap().toInstant(),
                zone
            )
        )

        val page = TimeGridLayout.build(
            DateRange(change, change.plusDays(1)),
            zone,
            listOf(first, second)
        )

        assertEquals(listOf(120, 120), page.timed.map { it.startMinute })
        assertEquals(listOf(0, 1), page.timed.map { it.column })
    }

    @Test
    fun `an event spanning the repeated hour never ends before it starts`() {
        val change = LocalDate.parse("2026-10-25")
        // 02:30 (first pass) to 02:15 (second pass) is 45 real minutes but reads backwards.
        val start = change.atTime(2, 30).atZone(madrid).withEarlierOffsetAtOverlap().toInstant()
        val end = change.atTime(2, 15).atZone(madrid).withLaterOffsetAtOverlap().toInstant()
        val event = EventInstance(
            EventId(1),
            CalendarId(1),
            "backwards",
            EventTime.Timed(start, end, madrid)
        )

        val block = TimeGridLayout.build(
            DateRange(change, change.plusDays(1)),
            madrid,
            listOf(event)
        ).timed.single()

        assertEquals(150, block.startMinute)
        assertEquals(150, block.endMinute)
    }

    @Test
    fun `the day boundaries of a 25 hour day are honoured`() {
        val change = LocalDate.parse("2026-10-25")
        val late = timed(at(change, 23), at(change.plusDays(1), 1))

        val page = TimeGridLayout.build(
            DateRange(change, change.plusDays(2)),
            madrid,
            listOf(late)
        )

        assertEquals(listOf(0, 1), page.timed.map { it.dayIndex })
        assertEquals(listOf(1440, 60), page.timed.map { it.endMinute })
    }

    @Test
    fun `minute of day and date follow the zone`() {
        val instant = LocalDateTime.parse("2026-03-11T23:30:45").atZone(madrid).toInstant()

        assertEquals(23 * 60 + 30, TimeGridLayout.minuteOfDay(instant, madrid))
        assertEquals(day, TimeGridLayout.dateOf(instant, madrid))
        assertEquals(day.plusDays(1), TimeGridLayout.dateOf(instant, tokyo))
        assertEquals(7 * 60 + 30, TimeGridLayout.minuteOfDay(instant, tokyo))
    }
}
