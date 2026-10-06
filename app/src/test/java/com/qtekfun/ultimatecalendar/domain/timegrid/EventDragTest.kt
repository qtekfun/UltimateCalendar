// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class EventDragTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val day = LocalDate.of(2026, 3, 11)
    private val oneDay = DayBounds(day, 1)
    private val threeDays = DayBounds(day, 3)

    private fun timed(from: LocalDateTime, to: LocalDateTime, zone: ZoneId = madrid) =
        EventTime.Timed(from.atZone(madrid).toInstant(), to.atZone(madrid).toInstant(), zone)

    private fun at(hour: Int, minute: Int = 0, offsetDays: Long = 0) =
        day.plusDays(offsetDays).atTime(hour, minute)

    private fun EventTime.Timed.startLocal() = start.atZone(madrid).toLocalDateTime()

    private fun EventTime.Timed.endLocal() = end.atZone(madrid).toLocalDateTime()

    private val meeting = timed(at(9), at(10))

    // --- moving -----------------------------------------------------------------------------

    @Test
    fun `the start snaps to the nearest quarter hour`() {
        assertEquals(at(9, 15), EventDrag.move(meeting, madrid, 0, 22f, oneDay).startLocal())
        assertEquals(at(9, 30), EventDrag.move(meeting, madrid, 0, 23f, oneDay).startLocal())
        assertEquals(at(8, 45), EventDrag.move(meeting, madrid, 0, -14f, oneDay).startLocal())
    }

    @Test
    fun `a move keeps the length of the event`() {
        val moved = EventDrag.move(meeting, madrid, 0, 90f, oneDay)

        assertEquals(at(10, 30), moved.startLocal())
        assertEquals(at(11, 30), moved.endLocal())
    }

    @Test
    fun `a finger that has hardly moved changes nothing`() {
        assertSame(meeting, EventDrag.move(meeting, madrid, 0, 7f, oneDay))
        assertSame(meeting, EventDrag.move(meeting, madrid, 0, -7f, oneDay))
        assertSame(meeting, EventDrag.resize(meeting, madrid, 7f))
    }

    @Test
    fun `an event off the quarter hours snaps when it is really moved`() {
        val odd = timed(at(9, 7), at(10, 7))

        assertEquals(at(9, 15), EventDrag.move(odd, madrid, 0, 8f, oneDay).startLocal())
        // Back where it was: no change at all, not a snap to 9:00.
        assertSame(odd, EventDrag.move(odd, madrid, 0, 0f, oneDay))
    }

    @Test
    fun `another day keeps the time of day`() {
        val moved = EventDrag.move(meeting, madrid, 2, 0f, threeDays)

        assertEquals(at(9, 0, 2), moved.startLocal())
        assertEquals(at(10, 0, 2), moved.endLocal())
    }

    @Test
    fun `an event that fits in a day does not run over midnight`() {
        val late = EventDrag.move(meeting, madrid, 0, 2000f, oneDay)

        assertEquals(at(23), late.startLocal())
        assertEquals(at(0, 0, 1), late.endLocal())
        val early = EventDrag.move(meeting, madrid, 0, -2000f, oneDay)
        assertEquals(at(0), early.startLocal())
    }

    @Test
    fun `an event of a few minutes can sit in the last quarter hour`() {
        val short = timed(at(9), at(9, 10))

        assertEquals(at(23, 45), EventDrag.move(short, madrid, 0, 2000f, oneDay).startLocal())
        val instant = timed(at(9), at(9))
        assertEquals(at(23, 45), EventDrag.move(instant, madrid, 0, 2000f, oneDay).startLocal())
    }

    @Test
    fun `the day is kept inside the page`() {
        // Nine days on is past the page: the last day, as late as the event fits.
        assertEquals(at(23, 0, 2), EventDrag.move(meeting, madrid, 9, 0f, threeDays).startLocal())
        assertEquals(at(0), EventDrag.move(meeting, madrid, -9, 0f, threeDays).startLocal())
    }

    @Test
    fun `a longer event only has to start inside the page`() {
        val night = timed(at(22), at(2, 0, 1))

        val moved = EventDrag.move(night, madrid, 0, 4000f, oneDay)

        // It may start in the last quarter hour and run on to the next day, four hours long.
        assertEquals(at(23, 45), moved.startLocal())
        assertEquals(Duration.ofHours(4), Duration.between(moved.start, moved.end))
    }

    @Test
    fun `an event that starts before the page can be moved onto it`() {
        val before = timed(at(22, 0, -1), at(2))

        val moved = EventDrag.move(before, madrid, 0, 120f, oneDay)

        assertEquals(at(0, 0), moved.startLocal())
        assertEquals(Duration.ofHours(4), Duration.between(moved.start, moved.end))
    }

    @Test
    fun `an event keeps the zone it has when it is moved`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val call = timed(at(9), at(10), tokyo)

        val moved = EventDrag.move(call, madrid, 0, 60f, oneDay)

        assertEquals(tokyo, moved.zone)
        assertEquals(call.start.plusSeconds(3600), moved.start)
        assertEquals(tokyo, EventDrag.resize(call, madrid, 60f).zone)
    }

    @Test
    fun `a time in the skipped hour moves forward to one that exists`() {
        val spring = LocalDate.of(2026, 3, 28)
        val bounds = DayBounds(spring, 2)
        val early = EventTime.Timed(
            spring.atTime(2, 30).atZone(madrid).toInstant(),
            spring.atTime(3, 30).atZone(madrid).toInstant(),
            madrid
        )

        val moved = EventDrag.move(early, madrid, 1, 0f, bounds)

        // 02:30 on the 29th does not exist: it is 03:30 (summer time), one hour long.
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), moved.start)
        assertEquals(Instant.parse("2026-03-29T02:30:00Z"), moved.end)
    }

    @Test
    fun `a time in the repeated hour is the first pass, never ambiguous`() {
        val autumn = LocalDate.of(2026, 10, 24)
        val bounds = DayBounds(autumn, 2)
        val early = EventTime.Timed(
            autumn.atTime(2, 30).atZone(madrid).toInstant(),
            autumn.atTime(3, 30).atZone(madrid).toInstant(),
            madrid
        )

        val moved = EventDrag.move(early, madrid, 1, 0f, bounds)

        // 02:30 on the 25th happens at 00:30Z (summer time) and at 01:30Z: the first one.
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), moved.start)
        assertEquals(Duration.ofHours(1), Duration.between(moved.start, moved.end))
    }

    @Test
    fun `an event moved over a clock change keeps its exact length`() {
        val spring = LocalDate.of(2026, 3, 29)
        val bounds = DayBounds(spring, 1)
        val night = EventTime.Timed(
            Instant.parse("2026-03-29T00:00:00Z"),
            Instant.parse("2026-03-29T02:00:00Z"),
            madrid
        )

        val moved = EventDrag.move(night, madrid, 0, -60f, bounds)

        // It started at 01:00 CET; one hour earlier is 00:00 CET. Two hours of real time.
        assertEquals(Instant.parse("2026-03-28T23:00:00Z"), moved.start)
        assertEquals(Duration.ofHours(2), Duration.between(moved.start, moved.end))
    }

    @Test
    fun `a finer step moves by exactly what is asked`() {
        val odd = timed(at(9, 7), at(10, 7))

        assertEquals(at(9, 22), EventDrag.move(odd, madrid, 0, 15f, oneDay, step = 1).startLocal())
    }

    // --- changing the duration -----------------------------------------------------------------

    @Test
    fun `the end snaps and the start stays`() {
        val longer = EventDrag.resize(meeting, madrid, 40f)

        assertEquals(meeting.start, longer.start)
        assertEquals(at(10, 45), longer.endLocal())
        assertEquals(at(9, 30), EventDrag.resize(meeting, madrid, -30f).endLocal())
    }

    @Test
    fun `an event is never made shorter than a quarter hour`() {
        assertEquals(at(9, 15), EventDrag.resize(meeting, madrid, -300f).endLocal())
        val odd = timed(at(9, 7), at(10, 7))
        // Fifteen minutes after 9:07 is 9:22: the end goes to the next quarter hour.
        assertEquals(at(9, 30), EventDrag.resize(odd, madrid, -300f).endLocal())
    }

    @Test
    fun `an event cannot end after the day it ends on`() {
        assertEquals(at(0, 0, 1), EventDrag.resize(meeting, madrid, 5000f).endLocal())
    }

    @Test
    fun `an event that ends at midnight can still be shortened`() {
        val evening = timed(at(22), at(0, 0, 1))

        assertEquals(at(23), EventDrag.resize(evening, madrid, -60f).endLocal())
        assertEquals(at(0, 0, 1), EventDrag.resize(evening, madrid, 60f).endLocal())
    }

    @Test
    fun `the end of a multi day event moves on the day it ends`() {
        val night = timed(at(22), at(2, 0, 1))

        val longer = EventDrag.resize(night, madrid, 60f)

        assertEquals(at(3, 0, 1), longer.endLocal())
        assertEquals(night.start, longer.start)
    }

    @Test
    fun `the shortest end of an event across midnight is on the next quarter hour`() {
        val night = timed(at(23, 50), at(0, 5, 1))

        val shorter = EventDrag.resize(night, madrid, -300f)

        assertEquals(at(0, 15, 1), shorter.endLocal())
    }

    @Test
    fun `the end never lands before the start in a repeated hour`() {
        val autumn = LocalDate.of(2026, 10, 25)
        // Started in the second pass of 02:30 (01:30Z), ends at 03:30 CET.
        val second = EventTime.Timed(
            Instant.parse("2026-10-25T01:30:00Z"),
            Instant.parse("2026-10-25T02:30:00Z"),
            madrid
        )

        val shorter = EventDrag.resize(second, madrid, -60f)

        assertEquals(second.start.plus(EventDrag.MIN_DURATION), shorter.end)
        assertEquals(autumn, shorter.start.atZone(madrid).toLocalDate())
    }

    @Test
    fun `an instant event can be given a length`() {
        val instant = timed(at(9), at(9))

        assertEquals(at(9, 30), EventDrag.resize(instant, madrid, 30f).endLocal())
        assertEquals(at(9, 15), EventDrag.resize(instant, madrid, -30f).endLocal())
    }

    // --- all-day events ------------------------------------------------------------------------

    @Test
    fun `an all day event moves by days and keeps its length`() {
        val trip = EventTime.AllDay(day, day.plusDays(2))

        val moved = EventDrag.moveAllDay(trip, 1, DayBounds(day, 7))

        assertEquals(EventTime.AllDay(day.plusDays(1), day.plusDays(3)), moved)
    }

    @Test
    fun `an all day event that has not moved is the same event`() {
        val trip = EventTime.AllDay(day, day.plusDays(2))

        assertSame(trip, EventDrag.moveAllDay(trip, 0, DayBounds(day, 7)))
    }

    @Test
    fun `an all day event keeps at least one day on the page`() {
        val trip = EventTime.AllDay(day.plusDays(2), day.plusDays(5))
        val bounds = DayBounds(day, 7)

        assertEquals(
            EventTime.AllDay(day.plusDays(6), day.plusDays(9)),
            EventDrag.moveAllDay(trip, 40, bounds)
        )
        assertEquals(
            EventTime.AllDay(day.minusDays(2), day.plusDays(1)),
            EventDrag.moveAllDay(trip, -40, bounds)
        )
    }
}
