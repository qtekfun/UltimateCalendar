// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecurrenceEngineSeriesTest {
    private val daily = "FREQ=DAILY"
    private val january = days("2026-01-01", "2026-02-01")

    @Test
    fun `EXDATE removes an occurrence of an all-day series`() {
        val result =
            expand(
                allDay("2026-01-01"),
                "$daily;COUNT=4",
                january,
                exDates = setOf(day("2026-01-02"))
            )
        assertEquals(listOf("2026-01-01", "2026-01-03", "2026-01-04"), result.starts())
    }

    @Test
    fun `EXDATE removes an occurrence of a timed series and ignores keys of the other kind`() {
        val result = expand(
            timed("2026-01-01T10:00"),
            "$daily;COUNT=3",
            january,
            exDates = setOf(
                moment("2026-01-02T10:00"),
                day("2026-01-03"),
                moment("2026-01-03T11:00")
            )
        )
        assertEquals(listOf("2026-01-01T10:00", "2026-01-03T10:00"), result.starts())
    }

    @Test
    fun `an excluded occurrence still counts towards COUNT`() {
        val result =
            expand(
                allDay("2026-01-01"),
                "$daily;COUNT=3",
                january,
                exDates = setOf(day("2026-01-01"))
            )
        assertEquals(listOf("2026-01-02", "2026-01-03"), result.starts())
    }

    @Test
    fun `RDATE adds occurrences with the length of the series`() {
        val result = expand(
            allDay("2026-01-01", days = 2),
            "$daily;COUNT=1",
            january,
            rDates = setOf(day("2026-01-10"))
        )
        assertEquals(listOf("2026-01-01", "2026-01-10"), result.starts())
        val added = result.instances().last().time as EventTime.AllDay
        assertEquals(allDay("2026-01-10", days = 2), added)
    }

    @Test
    fun `a timed RDATE keeps the duration and zone`() {
        val result = expand(
            timed("2026-01-01T10:00", minutes = 90),
            "$daily;COUNT=1",
            january,
            rDates = setOf(moment("2026-01-15T18:00"))
        )
        assertEquals(listOf("2026-01-01T10:00", "2026-01-15T18:00"), result.starts())
        val added = result.instances().last().time as EventTime.Timed
        assertEquals(Duration.ofMinutes(90), Duration.between(added.start, added.end))
        assertEquals(MADRID, added.zone)
    }

    @Test
    fun `an RDATE equal to a generated occurrence is not repeated, others of its kind ignored`() {
        val result = expand(
            allDay("2026-01-01"),
            "$daily;COUNT=2",
            january,
            rDates = setOf(day("2026-01-02"), moment("2026-01-20T10:00"))
        )
        assertEquals(listOf("2026-01-01", "2026-01-02"), result.starts())
        val timedResult = expand(
            timed("2026-01-01T10:00"),
            "$daily;COUNT=1",
            january,
            rDates = setOf(day("2026-01-20"))
        )
        assertEquals(listOf("2026-01-01T10:00"), timedResult.starts())
    }

    @Test
    fun `RDATE works without a rule and marks the instances as recurring`() {
        val result = expand(allDay("2026-01-01"), null, january, rDates = setOf(day("2026-01-05")))
        assertEquals(listOf("2026-01-01", "2026-01-05"), result.starts())
        assertTrue(result.instances().all { it.isRecurring })
    }

    @Test
    fun `EXDATE can remove an RDATE`() {
        val result = expand(
            allDay("2026-01-01"),
            null,
            january,
            exDates = setOf(day("2026-01-05")),
            rDates = setOf(day("2026-01-05"))
        )
        assertEquals(listOf("2026-01-01"), result.starts())
    }

    @Test
    fun `an override replaces the occurrence it names`() {
        val moved = event(
            timed("2026-01-02T15:00", minutes = 30),
            null,
            id = 9,
            title = "Gym (moved)"
        )
            .copy(location = "Pool", color = 0xFF0000)
        val result = expand(
            timed("2026-01-01T10:00"),
            "$daily;COUNT=3",
            january,
            overrides = listOf(OccurrenceOverride(moment("2026-01-02T10:00"), moved))
        )
        assertEquals(
            listOf("2026-01-01T10:00", "2026-01-02T15:00", "2026-01-03T10:00"),
            result.starts()
        )
        val instance = result.instances()[1]
        assertEquals("Gym (moved)", instance.title)
        assertEquals("Pool", instance.location)
        assertEquals(0xFF0000, instance.color)
        assertEquals(9L, instance.eventId.value)
        assertTrue(instance.isRecurring)
    }

    @Test
    fun `an override with no replacement cancels the occurrence`() {
        val result = expand(
            allDay("2026-01-01"),
            "$daily;COUNT=3",
            january,
            overrides = listOf(OccurrenceOverride(day("2026-01-02")))
        )
        assertEquals(listOf("2026-01-01", "2026-01-03"), result.starts())
    }

    @Test
    fun `an occurrence moved into the range shows even if its original is outside`() {
        val moved = event(allDay("2026-01-20"), null, id = 9)
        val result = expand(
            allDay("2026-03-01"),
            "$daily;COUNT=3",
            january,
            overrides = listOf(OccurrenceOverride(day("2026-03-01"), moved))
        )
        assertEquals(listOf("2026-01-20"), result.starts())
    }

    @Test
    fun `an occurrence moved out of the range disappears from it`() {
        val moved = event(allDay("2026-05-01"), null, id = 9)
        val result = expand(
            allDay("2026-01-01"),
            "$daily;COUNT=2",
            january,
            overrides = listOf(OccurrenceOverride(day("2026-01-01"), moved))
        )
        assertEquals(listOf("2026-01-02"), result.starts())
    }

    @Test
    fun `an override of an RDATE replaces it`() {
        val moved = event(allDay("2026-01-07"), null, id = 9)
        val result = expand(
            allDay("2026-01-01"),
            null,
            january,
            rDates = setOf(day("2026-01-05")),
            overrides = listOf(OccurrenceOverride(day("2026-01-05"), moved))
        )
        assertEquals(listOf("2026-01-01", "2026-01-07"), result.starts())
    }

    @Test
    fun `instances with the same start are ordered by event id`() {
        val five = event(allDay("2026-01-02"), null, id = 5)
        val zero = event(allDay("2026-01-02"), null, id = 0)
        val result = expand(
            allDay("2026-01-01"),
            "$daily;COUNT=3",
            january,
            overrides = listOf(
                OccurrenceOverride(day("2026-01-01"), five),
                OccurrenceOverride(day("2026-01-03"), zero)
            )
        )
        assertEquals(listOf("2026-01-02", "2026-01-02", "2026-01-02"), result.starts())
        assertEquals(listOf(0L, 1L, 5L), result.instances().map { it.eventId.value })
    }

    @Test
    fun `the range is half open`() {
        val utc = ZoneId.of("UTC")
        val series = timed("2026-01-01T10:00", minutes = 60, zone = utc)

        fun starts(from: String, to: String) =
            expand(series, "$daily;COUNT=3", instants(from, to)).starts(utc)
        // Starting exactly at the end of the range is outside it.
        assertEquals(
            listOf("2026-01-01T10:00"),
            starts("2026-01-01T00:00:00Z", "2026-01-02T10:00:00Z")
        )
        // Starting exactly at the start of the range is inside.
        assertEquals(
            listOf("2026-01-02T10:00"),
            starts("2026-01-02T10:00:00Z", "2026-01-02T10:00:01Z")
        )
        // Ending exactly at the start of the range is outside; one second earlier it is inside.
        assertEquals(emptyList<String>(), starts("2026-01-02T11:00:00Z", "2026-01-03T10:00:00Z"))
        assertEquals(
            listOf("2026-01-02T10:00"),
            starts("2026-01-02T10:59:59Z", "2026-01-03T10:00:00Z")
        )
    }

    @Test
    fun `an event with no length counts only inside the range`() {
        val at = Instant.parse("2026-01-02T10:00:00Z")
        val instant = EventTime.Timed(at, at, ZoneId.of("UTC"))
        assertEquals(
            1,
            expand(
                instant,
                null,
                instants("2026-01-02T10:00:00Z", "2026-01-02T11:00:00Z")
            ).instances().size
        )
        assertEquals(
            0,
            expand(
                instant,
                null,
                instants("2026-01-02T10:00:01Z", "2026-01-02T11:00:00Z")
            ).instances().size
        )
        assertEquals(
            0,
            expand(
                instant,
                null,
                instants("2026-01-02T09:00:00Z", "2026-01-02T10:00:00Z")
            ).instances().size
        )
    }

    @Test
    fun `a long occurrence that began before the range still shows`() {
        val result = expand(allDay("2025-12-30", days = 5), "FREQ=WEEKLY;COUNT=2", january)
        assertEquals(listOf("2025-12-30", "2026-01-06"), result.starts())
        assertFalse(result.instances().isEmpty())
    }

    @Test
    fun `all-day instances are placed on the timeline with the given zone`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val range = instants("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z")
        // Tokyo's 31 December runs 15:00Z-15:00Z, so 31 Dec and 1-2 Jan touch the range there...
        val inTokyo = expand(allDay("2025-12-31"), "$daily;COUNT=5", range, zone = tokyo)
        assertEquals(listOf("2026-01-01", "2026-01-02"), inTokyo.starts())
        // ...while in UTC only 1 January does.
        val inUtc = expand(allDay("2025-12-31"), "$daily;COUNT=5", range)
        assertEquals(listOf("2026-01-01"), inUtc.starts())
    }

    @Test
    fun `instances carry the data of the series`() {
        val master = event(allDay("2026-01-01"), "$daily;COUNT=1", id = 4, title = "Rent")
            .copy(location = "Home", color = 5)
        val result = RecurrenceEngine.expand(EventSeries(master), january).instances().single()
        assertEquals(master.id, result.eventId)
        assertEquals(master.calendarId, result.calendarId)
        assertEquals("Rent", result.title)
        assertEquals("Home", result.location)
        assertEquals(5, result.color)
        assertTrue(result.isRecurring)
    }
}
