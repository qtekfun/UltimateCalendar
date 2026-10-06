// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IcsDateTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun read(line: String) = IcsDate.from(IcsParser.parseLine(line))

    @Test
    fun `reads all-day, UTC, zoned and floating values`() {
        assertEquals(IcsDate("2026-10-05"), read("DTSTART;VALUE=DATE:20261005"))
        assertEquals(IcsDate("2026-10-05T09:30", "UTC"), read("DTSTART:20261005T093000Z"))
        assertEquals(
            IcsDate("2026-10-05T09:30:15", "Europe/Madrid"),
            read("DTSTART;TZID=Europe/Madrid:20261005T093015")
        )
        assertEquals(IcsDate("2026-10-05T09:30"), read("DTSTART:20261005T093000"))
        assertTrue(read("DTSTART;VALUE=DATE:20261005")!!.allDay)
        assertFalse(read("DTSTART:20261005T093000")!!.allDay)
    }

    @Test
    fun `invalid values are null`() {
        assertNull(read("DTSTART:tomorrow"))
        assertNull(read("DTSTART:20261345"))
    }

    @Test
    fun `writes each kind back as it was`() {
        listOf(
            "DTSTART;VALUE=DATE:20261005",
            "DTSTART:20261005T093000Z",
            "DTSTART;TZID=Europe/Madrid:20261005T093015",
            "DTSTART:20261005T093000"
        ).forEach { line ->
            assertEquals(line, IcsWriter.contentLine(IcsDate.property("DTSTART", read(line)!!)))
        }
    }

    @Test
    fun `instants use the zone, or the fallback for floating, all-day and unknown zones`() {
        assertEquals(
            Instant.parse("2026-10-05T07:30:00Z"),
            IcsDate("2026-10-05T09:30", "Europe/Madrid").toInstant(ZoneOffset.UTC)
        )
        assertEquals(
            Instant.parse("2026-10-05T09:30:00Z"),
            IcsDate("2026-10-05T09:30", "UTC").toInstant(madrid)
        )
        assertEquals(
            Instant.parse("2026-10-05T07:30:00Z"),
            IcsDate("2026-10-05T09:30").toInstant(madrid)
        )
        assertEquals(Instant.parse("2026-10-04T22:00:00Z"), IcsDate("2026-10-05").toInstant(madrid))
        assertEquals(
            Instant.parse("2026-10-05T07:30:00Z"),
            IcsDate("2026-10-05T09:30", "Mars/Olympus").toInstant(madrid)
        )
    }

    @Test
    fun `UTC stamps`() {
        assertEquals("20261003T101530Z", IcsDate.utc(Instant.parse("2026-10-03T10:15:30.999Z")))
        assertEquals(
            Instant.parse("2026-10-03T10:15:30Z"),
            IcsDate.parseUtc(IcsParser.parseLine("X:20261003T101530Z"))
        )
        assertNull(IcsDate.parseUtc(IcsParser.parseLine("X;VALUE=DATE:20261003")))
        assertNull(IcsDate.parseUtc(null))
    }
}
