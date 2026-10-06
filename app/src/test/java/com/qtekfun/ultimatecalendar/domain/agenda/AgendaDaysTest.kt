// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.agenda

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

class AgendaDaysTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val first = LocalDate.parse("2026-03-10")
    private val range = DateRange(first, first.plusDays(10))
    private var lastId = 0L

    private fun timed(
        title: String,
        from: LocalDateTime,
        to: LocalDateTime,
        eventZone: ZoneId = madrid,
        color: Int? = null,
        calendar: Long = 1,
        status: AttendeeStatus? = null,
        zone: ZoneId = madrid
    ) = EventInstance(
        eventId = EventId(++lastId),
        calendarId = CalendarId(calendar),
        title = title,
        time = EventTime.Timed(
            from.atZone(zone).toInstant(),
            to.atZone(zone).toInstant(),
            eventZone
        ),
        color = color,
        selfStatus = status
    )

    private fun allDay(title: String, from: LocalDate, to: LocalDate) = EventInstance(
        eventId = EventId(++lastId),
        calendarId = CalendarId(1),
        title = title,
        time = EventTime.AllDay(from, to)
    )

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        first.plusDays(day.toLong()).atTime(hour, minute)

    private fun build(vararg instances: EventInstance, zone: ZoneId = madrid) =
        AgendaDays.build(range, zone, instances.toList())

    @Test
    fun `no events give no days`() {
        assertTrue(build().isEmpty())
    }

    @Test
    fun `days without events are skipped and the others come in date order`() {
        val days = build(
            timed("Late", at(5, 9), at(5, 10)),
            timed("Early", at(1, 9), at(1, 10))
        )

        assertEquals(listOf(first.plusDays(1), first.plusDays(5)), days.map { it.date })
    }

    @Test
    fun `unsorted input is ordered with all-day first and then by start`() {
        val days = build(
            timed("Afternoon", at(0, 15), at(0, 16)),
            allDay("Holiday", first, first.plusDays(1)),
            timed("Morning", at(0, 8), at(0, 9)),
            allDay("Another holiday", first, first.plusDays(1))
        )

        assertEquals(
            listOf("Another holiday", "Holiday", "Morning", "Afternoon"),
            days.single().entries.map { it.instance.title }
        )
    }

    @Test
    fun `events starting together are ordered by title and then by id`() {
        val a = timed("same", at(0, 9), at(0, 10))
        val b = timed("Same", at(0, 9), at(0, 10))
        val c = timed("Aaa", at(0, 9), at(0, 10))

        val order = build(b, a, c).single().entries.map { it.instance }

        assertEquals(listOf(c, a, b), order)
    }

    @Test
    fun `a multi-day all-day event appears on each of its days`() {
        val days = build(allDay("Trip", first.plusDays(1), first.plusDays(4)))

        assertEquals(
            listOf(first.plusDays(1), first.plusDays(2), first.plusDays(3)),
            days.map { it.date }
        )
        assertTrue(days.all { it.entries.single().slot == AgendaSlot.AllDay })
    }

    @Test
    fun `an event crossing midnight shows from on the first day and until on the second`() {
        val days = build(timed("Night shift", at(2, 22), at(3, 6)))

        assertEquals(listOf(first.plusDays(2), first.plusDays(3)), days.map { it.date })
        assertEquals(
            AgendaSlot.From(at(2, 22).atZone(madrid).toInstant()),
            days[0].entries.single().slot
        )
        assertEquals(
            AgendaSlot.Until(at(3, 6).atZone(madrid).toInstant()),
            days[1].entries.single().slot
        )
    }

    @Test
    fun `a timed event across several days covers the middle ones`() {
        val days = build(timed("Conference", at(1, 18), at(4, 12)))

        assertEquals(4, days.size)
        assertTrue(days[0].entries.single().slot is AgendaSlot.From)
        assertEquals(AgendaSlot.AllDay, days[1].entries.single().slot)
        assertEquals(AgendaSlot.AllDay, days[2].entries.single().slot)
        assertTrue(days[3].entries.single().slot is AgendaSlot.Until)
    }

    @Test
    fun `an event ending at midnight does not appear on the next day`() {
        val days = build(timed("Late", at(2, 20), at(3, 0)))

        assertEquals(listOf(first.plusDays(2)), days.map { it.date })
        assertTrue(days.single().entries.single().slot is AgendaSlot.Span)
    }

    @Test
    fun `an event with no length is listed once at its start`() {
        val days = build(timed("Marker", at(2, 9), at(2, 9)))

        val entry = days.single().entries.single()
        assertEquals(AgendaSlot.From(at(2, 9).atZone(madrid).toInstant()), entry.slot)
    }

    @Test
    fun `a same-day event is a span with its own start and end`() {
        val entry = build(timed("Standup", at(0, 9), at(0, 9, 30))).single().entries.single()

        assertEquals(
            AgendaSlot.Span(
                at(0, 9).atZone(madrid).toInstant(),
                at(0, 9, 30).atZone(madrid).toInstant()
            ),
            entry.slot
        )
    }

    @Test
    fun `days follow the device zone, not the event's`() {
        // 07:30 in Tokyo is 23:30 the day before in Madrid, in winter.
        val instance =
            timed("Early call", at(2, 7, 30), at(2, 7, 50), zone = tokyo, eventZone = tokyo)

        val days = build(instance, zone = madrid)

        assertEquals(listOf(first.plusDays(1)), days.map { it.date })
        assertEquals(tokyo, days.single().entries.single().otherZone)
    }

    @Test
    fun `an event in the same zone offset has no other-zone hint`() {
        val entry = build(timed("Local", at(0, 9), at(0, 10))).single().entries.single()

        assertNull(entry.otherZone)
    }

    @Test
    fun `all-day events stay on their dates in any zone and have no zone hint`() {
        val instance = allDay("Birthday", first.plusDays(2), first.plusDays(3))

        val tokyoDays = build(instance, zone = tokyo)
        val madridDays = build(instance, zone = madrid)

        assertEquals(listOf(first.plusDays(2)), tokyoDays.map { it.date })
        assertEquals(tokyoDays.map { it.date }, madridDays.map { it.date })
        assertNull(tokyoDays.single().entries.single().otherZone)
    }

    @Test
    fun `events outside the range are dropped and long ones are cut to it`() {
        val days = build(
            timed("Before", at(-3, 9), at(-3, 10)),
            timed("After", at(12, 9), at(12, 10)),
            allDay("Whole month", first.minusDays(20), first.plusDays(40))
        )

        assertEquals(10, days.size)
        assertEquals(first, days.first().date)
        assertEquals(first.plusDays(9), days.last().date)
        assertTrue(days.all { it.entries.single().instance.title == "Whole month" })
    }

    @Test
    fun `an event that starts before the range shows until on its first day`() {
        val days = build(timed("Overnight", at(-1, 23), at(0, 7)))

        assertEquals(listOf(first), days.map { it.date })
        assertTrue(days.single().entries.single().slot is AgendaSlot.Until)
    }

    @Test
    fun `a spring forward day of 23 hours holds an event across the missing hour`() {
        val change = LocalDate.parse("2026-03-29")
        val days = AgendaDays.build(
            DateRange(change.minusDays(1), change.plusDays(2)),
            madrid,
            listOf(
                timed("Across the gap", change.atTime(1, 30), change.atTime(3, 30)),
                timed("Whole day", change.atStartOfDay(), change.plusDays(1).atStartOfDay())
            )
        )

        assertEquals(listOf(change), days.map { it.date })
        val (whole, gap) = days.single().entries
        assertEquals("Whole day", whole.instance.title)
        assertEquals(AgendaSlot.AllDay, whole.slot)
        assertTrue(gap.slot is AgendaSlot.Span)
    }

    @Test
    fun `a fall back day of 25 hours is covered by an event as long as the day`() {
        val change = LocalDate.parse("2026-10-25")
        val start = change.atStartOfDay(madrid).toInstant()
        val end = change.plusDays(1).atStartOfDay(madrid).toInstant()
        val covering = EventInstance(
            EventId(1),
            CalendarId(1),
            "25 hours",
            EventTime.Timed(start, end, madrid)
        )
        // Twenty-four hours from midnight ends at 23:00, an hour before the day does.
        val shorter = EventInstance(
            EventId(2),
            CalendarId(1),
            "24 hours",
            EventTime.Timed(start, start.plusSeconds(24 * 3600), madrid)
        )

        val days = AgendaDays.build(
            DateRange(change.minusDays(1), change.plusDays(2)),
            madrid,
            listOf(covering, shorter)
        )

        assertEquals(listOf(change), days.map { it.date })
        assertEquals(
            AgendaSlot.AllDay,
            days.single().entries.first { it.instance.eventId == EventId(1) }.slot
        )
        assertTrue(
            days.single().entries.first {
                it.instance.eventId == EventId(2)
            }.slot is AgendaSlot.Span
        )
    }

    @Test
    fun `an event keeps its own color and otherwise takes its calendar's`() {
        val days = AgendaDays.build(
            range,
            madrid,
            listOf(
                timed("Painted", at(0, 8), at(0, 9), color = 0xFF111111.toInt(), calendar = 2),
                timed("Inherits", at(0, 10), at(0, 11), calendar = 2),
                timed("Unknown", at(0, 12), at(0, 13), calendar = 3)
            ),
            mapOf(CalendarId(2) to 0xFF222222.toInt())
        )

        assertEquals(
            listOf(0xFF111111.toInt(), 0xFF222222.toInt(), null),
            days.single().entries.map { it.color }
        )
    }

    @Test
    fun `only an unanswered invitation is pending`() {
        val entries = build(
            timed("Mine", at(0, 8), at(0, 9)),
            timed("Asked", at(0, 10), at(0, 11), status = AttendeeStatus.NEEDS_ACTION),
            timed("Accepted", at(0, 12), at(0, 13), status = AttendeeStatus.ACCEPTED)
        ).single().entries

        assertEquals(listOf(false, true, false), entries.map { it.isPending })
        assertFalse(entries.first().isPending)
    }
}
