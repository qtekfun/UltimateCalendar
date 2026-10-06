// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IcsZonesTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun `IANA ids, with or without a path in front`() {
        assertEquals(madrid, IcsZones.resolve("Europe/Madrid"))
        assertEquals(madrid, IcsZones.resolve(" \"Europe/Madrid\" "))
        assertEquals(madrid, IcsZones.resolve("/mozilla.org/20050126_1/Europe/Madrid"))
        assertEquals(
            ZoneId.of("America/Argentina/Buenos_Aires"),
            IcsZones.resolve(
                "/freeassociation.sourceforge.net/Tzfile/America/Argentina/Buenos_Aires"
            )
        )
        assertEquals(ZoneId.of("UTC"), IcsZones.resolve("UTC"))
    }

    @ParameterizedTest
    @CsvSource(
        "W. Europe Standard Time, Europe/Berlin",
        "romance standard time, Europe/Paris",
        "GMT STANDARD TIME, Europe/London",
        "Eastern Standard Time, America/New_York",
        "Pacific Standard Time, America/Los_Angeles",
        "China Standard Time, Asia/Shanghai",
        "Tokyo Standard Time, Asia/Tokyo",
        "India Standard Time, Asia/Calcutta",
        "AUS Eastern Standard Time, Australia/Sydney"
    )
    fun `Windows zone names`(name: String, iana: String) {
        assertEquals(ZoneId.of(iana), IcsZones.resolve(name))
    }

    @Test
    fun `every Windows name in the table is a zone this JVM knows`() {
        val names = IcsZones.windowsNames

        assertTrue(names.size > 80)
        names.forEach { assertNotNull(IcsZones.resolve(it), it) }
    }

    @Test
    fun `an unknown name is only explained by the VTIMEZONE of the file`() {
        assertNull(IcsZones.resolve("Corporate Time Zone 7"))
        assertNull(
            IcsZones.resolve(
                "Corporate Time Zone 7",
                calendarOf("BEGIN:VTIMEZONE\nTZID:Other\nEND:VTIMEZONE\n")
            )
        )

        val location = calendarOf(
            "BEGIN:VTIMEZONE\nTZID:Corporate Time Zone 7\nX-LIC-LOCATION:Asia/Tokyo\nEND:VTIMEZONE\n"
        )
        assertEquals(ZoneId.of("Asia/Tokyo"), IcsZones.resolve("Corporate Time Zone 7", location))

        val wrTimezone = calendarOf(
            "BEGIN:VTIMEZONE\nTZID:Corporate Time Zone 7\nX-WR-TIMEZONE:Asia/Seoul\nEND:VTIMEZONE\n"
        )
        assertEquals(ZoneId.of("Asia/Seoul"), IcsZones.resolve("Corporate Time Zone 7", wrTimezone))

        val standard = calendarOf(
            "BEGIN:VTIMEZONE\nTZID:Corporate Time Zone 7\n" +
                "BEGIN:DAYLIGHT\nTZOFFSETTO:+0600\nEND:DAYLIGHT\n" +
                "BEGIN:STANDARD\nTZOFFSETTO:+0530\nEND:STANDARD\nEND:VTIMEZONE\n"
        )
        assertEquals(
            ZoneOffset.ofHoursMinutes(5, 30),
            IcsZones.resolve("Corporate Time Zone 7", standard)
        )

        val onlyDaylight = calendarOf(
            "BEGIN:VTIMEZONE\nTZID:Corporate Time Zone 7\nBEGIN:DAYLIGHT\nTZOFFSETTO:-0300\n" +
                "END:DAYLIGHT\nEND:VTIMEZONE\n"
        )
        assertEquals(
            ZoneOffset.ofHours(-3),
            IcsZones.resolve("Corporate Time Zone 7", onlyDaylight)
        )

        val noOffset =
            calendarOf(
                "BEGIN:VTIMEZONE\nTZID:Corporate Time Zone 7\nBEGIN:STANDARD\nEND:STANDARD\nEND:VTIMEZONE\n"
            )
        assertNull(IcsZones.resolve("Corporate Time Zone 7", noOffset))
        val noBlocks = calendarOf("BEGIN:VTIMEZONE\nTZID:Corporate Time Zone 7\nEND:VTIMEZONE\n")
        assertNull(IcsZones.resolve("Corporate Time Zone 7", noBlocks))
    }

    @ParameterizedTest
    @CsvSource("+0100, 3600", "-0530, -19800", "+010203, 3723", "+0000, 0")
    fun `UTC offsets`(text: String, seconds: Int) {
        assertEquals(ZoneOffset.ofTotalSeconds(seconds), IcsZones.parseOffset(text))
    }

    @Test
    fun `malformed offsets are refused`() {
        listOf("0100", "+01", "+01:00", "+0a00", "+9900", "", "+01000").forEach {
            assertNull(IcsZones.parseOffset(it), it)
        }
    }

    @Test
    fun `UTC is written with a Z, regions with a TZID`() {
        assertTrue(IcsZones.isUtc(ZoneOffset.UTC))
        assertTrue(IcsZones.isUtc(ZoneId.of("UTC")))
        assertTrue(IcsZones.isUtc(ZoneId.of("GMT")))
        assertTrue(IcsZones.isUtc(ZoneOffset.ofHours(2)))
        assertFalse(IcsZones.isUtc(madrid))
    }

    @Test
    fun `the resolver reads floating, UTC, named and unknown zones`() {
        val zones = IcsZones.Resolver(null, madrid)

        assertEquals(madrid, zones.zoneOf(IcsDate("2026-10-05T09:30")))
        assertEquals(ZoneOffset.UTC, zones.zoneOf(IcsDate("2026-10-05T09:30", IcsDate.UTC)))
        assertEquals(
            ZoneId.of("Asia/Tokyo"),
            zones.zoneOf(IcsDate("2026-10-05T09:30", "Asia/Tokyo"))
        )
        assertEquals(madrid, zones.zoneOf(IcsDate("2026-10-05T09:30", "Nowhere Standard Time")))
        assertEquals(
            Instant.parse("2026-10-05T00:30:00Z"),
            zones.instant(IcsDate("2026-10-05T09:30", "Asia/Tokyo"))
        )
    }

    private fun calendarOf(body: String) =
        IcsParser.parse("BEGIN:VCALENDAR\n$body" + "END:VCALENDAR\n").single()
}
