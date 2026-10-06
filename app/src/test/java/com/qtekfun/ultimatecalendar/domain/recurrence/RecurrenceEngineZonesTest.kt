// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecurrenceEngineZonesTest {
    private val year = days("2026-01-01", "2027-01-01")

    private fun instants(result: Expansion) = result.instances().map { it.time as EventTime.Timed }

    @Test
    fun `a daily 02-30 in Madrid moves forward when the clocks spring ahead`() {
        // 29 March 2026: 02:00 becomes 03:00, so 02:30 does not exist.
        val result = expand(timed("2026-03-27T02:30"), "FREQ=DAILY;COUNT=4", year)
        assertEquals(
            listOf("2026-03-27T02:30", "2026-03-28T02:30", "2026-03-29T03:30", "2026-03-30T02:30"),
            result.starts()
        )
        assertEquals(
            Instant.parse("2026-03-29T01:30:00Z"),
            instants(result)[2].start
        )
    }

    @Test
    fun `a daily 02-30 in Madrid takes the first of the two when the clocks go back`() {
        // 25 October 2026: 03:00 becomes 02:00, so 02:30 happens twice.
        val result = expand(timed("2026-10-24T02:30"), "FREQ=DAILY;COUNT=3", year)
        assertEquals(
            listOf("2026-10-24T02:30", "2026-10-25T02:30", "2026-10-26T02:30"),
            result.starts()
        )
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), instants(result)[1].start)
        assertEquals(Instant.parse("2026-10-26T01:30:00Z"), instants(result)[2].start)
    }

    @Test
    fun `a series that starts in the second of the two hours keeps its own first occurrence`() {
        val second = Instant.parse("2026-10-25T01:30:00Z") // 02:30 CET, the repeated hour
        val start = EventTime.Timed(second, second.plusSeconds(3600), MADRID)
        val result = expand(start, "FREQ=DAILY;COUNT=2", year)
        assertEquals(second, instants(result)[0].start)
        assertEquals(Instant.parse("2026-10-26T01:30:00Z"), instants(result)[1].start)
    }

    @Test
    fun `a weekly 10-00 keeps its wall time across both changes of the clocks`() {
        val result = expand(
            timed("2026-03-20T10:00"),
            "FREQ=WEEKLY;COUNT=2",
            year
        )
        assertEquals(listOf("2026-03-20T10:00", "2026-03-27T10:00"), result.starts())
        val across = expand(timed("2026-03-27T10:00"), "FREQ=WEEKLY;COUNT=2", year)
        assertEquals(listOf("2026-03-27T10:00", "2026-04-03T10:00"), across.starts())
        // One week is 167 hours when the clocks spring ahead.
        val (first, second) = instants(across)
        assertEquals(Duration.ofHours(167), Duration.between(first.start, second.start))
        val back = instants(expand(timed("2026-10-23T10:00"), "FREQ=WEEKLY;COUNT=2", year))
        assertEquals(Duration.ofHours(169), Duration.between(back[0].start, back[1].start))
    }

    @Test
    fun `the length is exact in real time for an occurrence moved out of the gap`() {
        val result = expand(timed("2026-03-28T02:30", minutes = 90), "FREQ=DAILY;COUNT=2", year)
        val second = instants(result)[1]
        assertEquals(Duration.ofMinutes(90), Duration.between(second.start, second.end))
        assertEquals("2026-03-29T03:30", second.start.atZone(MADRID).toLocalDateTime().toString())
        assertEquals("2026-03-29T05:00", second.end.atZone(MADRID).toLocalDateTime().toString())
    }

    @Test
    fun `New York springs ahead and falls back on its own dates`() {
        val spring = expand(timed("2026-03-07T02:30", zone = NEW_YORK), "FREQ=DAILY;COUNT=3", year)
        assertEquals(
            listOf("2026-03-07T02:30", "2026-03-08T03:30", "2026-03-09T02:30"),
            spring.starts(NEW_YORK)
        )
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), instants(spring)[1].start)

        val fall = expand(timed("2026-10-31T01:30", zone = NEW_YORK), "FREQ=DAILY;COUNT=3", year)
        assertEquals(
            listOf("2026-10-31T01:30", "2026-11-01T01:30", "2026-11-02T01:30"),
            fall.starts(NEW_YORK)
        )
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), instants(fall)[1].start)
        assertEquals(Instant.parse("2026-11-02T06:30:00Z"), instants(fall)[2].start)
    }

    @Test
    fun `a New York series is expanded in New York even when the phone is in Madrid`() {
        // 23:30 in New York is already the next day in Madrid; the rule follows New York's days.
        val result = expand(
            timed("2026-01-02T23:30", zone = NEW_YORK),
            "FREQ=WEEKLY;BYDAY=FR;COUNT=3",
            year,
            zone = MADRID
        )
        assertEquals(
            listOf("2026-01-02T23:30", "2026-01-09T23:30", "2026-01-16T23:30"),
            result.starts(NEW_YORK)
        )
        assertEquals(
            listOf("2026-01-03T05:30", "2026-01-10T05:30", "2026-01-17T05:30"),
            result.starts(MADRID)
        )
    }

    @Test
    fun `an exception to a series in another zone is matched by its instant`() {
        val result = expand(
            timed("2026-01-02T23:30", zone = NEW_YORK),
            "FREQ=DAILY;COUNT=3",
            year,
            exDates = setOf(moment("2026-01-04T05:30", MADRID)),
            zone = ZoneId.of("Asia/Tokyo")
        )
        assertEquals(listOf("2026-01-02T23:30", "2026-01-04T23:30"), result.starts(NEW_YORK))
    }

    @Test
    fun `a monthly series on the 31st keeps its wall time through daylight saving`() {
        val result = expand(timed("2026-01-31T09:00"), "FREQ=MONTHLY;COUNT=3", year)
        assertEquals(
            listOf("2026-01-31T09:00", "2026-03-31T09:00", "2026-05-31T09:00"),
            result.starts()
        )
    }
}
