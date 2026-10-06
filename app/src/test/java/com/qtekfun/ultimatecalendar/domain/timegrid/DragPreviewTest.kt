// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DragPreviewTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val day = LocalDate.of(2026, 3, 11)
    private val range = DateRange(day, day.plusDays(3))
    private val blue = 0xFF0000FF.toInt()
    private val green = 0xFF00FF00.toInt()

    private fun instant(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        date.atTime(hour, minute).atZone(zone).toInstant()

    private fun timed(id: Long, from: Int, to: Int, color: Int? = null, calendar: Long = 1) =
        EventInstance(
            EventId(id),
            CalendarId(calendar),
            "Event $id",
            EventTime.Timed(instant(day, from), instant(day, to), zone),
            color = color
        )

    private fun build(vararg events: EventInstance, colors: Map<CalendarId, Int> = emptyMap()) =
        TimeGridLayout.build(range, zone, events.toList(), colors)

    private fun TimeGridPage.blockOf(id: Long) = timed.first { it.instance.eventId.value == id }

    @Test
    fun `the others are laid out again around the moved event`() {
        val first = timed(1, 9, 10)
        val second = timed(2, 9, 10)
        val page = build(first, second)
        assertEquals(2, page.blockOf(1).columns)

        val preview = DragPreview.apply(
            page,
            zone,
            second,
            EventTime.Timed(
                instant(day, 11),
                instant(day, 12),
                zone
            )
        )

        assertEquals(1, preview.blockOf(1).columns)
        assertEquals(11 * 60, preview.blockOf(2).startMinute)
        assertEquals(1, preview.blockOf(2).columns)
    }

    @Test
    fun `moving onto another event makes them share the width`() {
        val first = timed(1, 9, 10)
        val second = timed(2, 12, 13)
        val page = build(first, second)

        val preview = DragPreview.apply(
            page,
            zone,
            second,
            EventTime.Timed(instant(day, 9, 30), instant(day, 10, 30), zone)
        )

        assertEquals(2, preview.blockOf(1).columns)
        assertEquals(2, preview.blockOf(2).columns)
    }

    @Test
    fun `an event moved to another day is drawn on that day`() {
        val page = build(timed(1, 9, 10))
        val moved = EventTime.Timed(instant(day.plusDays(2), 9), instant(day.plusDays(2), 10), zone)

        val preview = DragPreview.apply(page, zone, timed(1, 9, 10), moved)

        assertEquals(listOf(2), preview.timed.map { it.dayIndex })
    }

    @Test
    fun `an event that is not on the page is added where it lands`() {
        val page = build(timed(1, 9, 10))
        val stranger = timed(7, 9, 10).copy(
            time = EventTime.Timed(
                instant(day.minusDays(7), 9),
                instant(day.minusDays(7), 10),
                zone
            )
        )
        val landing = EventTime.Timed(
            instant(day.plusDays(1), 14),
            instant(day.plusDays(1), 15),
            zone
        )

        val preview = DragPreview.apply(page, zone, stranger, landing)

        assertEquals(setOf(1L, 7L), preview.timed.map { it.instance.eventId.value }.toSet())
        assertEquals(1, preview.blockOf(7).dayIndex)
        assertEquals(14 * 60, preview.blockOf(7).startMinute)
    }

    @Test
    fun `an event already saved at its new time is drawn once`() {
        val original = timed(1, 9, 10)
        val landing = EventTime.Timed(instant(day, 11), instant(day, 12), zone)
        // The page has been read again: it already has the event at the new time.
        val reloaded = build(original.copy(time = landing))

        val preview = DragPreview.apply(reloaded, zone, original, landing)

        assertEquals(1, preview.timed.size)
    }

    @Test
    fun `colors are kept, also when the event comes from another page`() {
        val own = timed(1, 9, 10, color = blue)
        val calendarColored = timed(2, 11, 12, calendar = 5)
        val page = build(own, calendarColored, colors = mapOf(CalendarId(5) to green))
        val stranger = timed(9, 9, 10, calendar = 8)
        val landing = EventTime.Timed(instant(day, 15), instant(day, 16), zone)

        val preview = DragPreview.apply(page, zone, stranger, landing, color = green)

        assertEquals(blue, preview.blockOf(1).color)
        assertEquals(green, preview.blockOf(2).color)
        assertEquals(green, preview.blockOf(9).color)
    }

    @Test
    fun `without a known color the event has none`() {
        val page = build(timed(1, 9, 10))
        val stranger = timed(9, 9, 10, calendar = 8)

        val preview = DragPreview.apply(
            page,
            zone,
            stranger,
            EventTime.Timed(instant(day, 15), instant(day, 16), zone)
        )

        assertNull(preview.blockOf(9).color)
    }

    @Test
    fun `an all day event moves in the strip`() {
        val trip = EventInstance(
            EventId(3),
            CalendarId(1),
            "Trip",
            EventTime.AllDay(day, day.plusDays(1)),
            color = green
        )
        val page = build(trip)

        val preview = DragPreview.apply(
            page,
            zone,
            trip,
            EventTime.AllDay(day.plusDays(1), day.plusDays(3))
        )

        val bar = preview.allDay.single()
        assertEquals(1, bar.firstDay)
        assertEquals(2, bar.lastDay)
    }
}
