// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VTimeZonesTest {
    private fun blocks(zone: String) = VTimeZones.of(zone)!!.components.associate { block ->
        block.name to
            listOf("DTSTART", "RRULE", "TZOFFSETFROM", "TZOFFSETTO").map {
                block.property(it)?.value
            }
    }

    @Test
    fun `European summer time ends and starts on the last Sunday`() {
        val zone = VTimeZones.of("Europe/Madrid")!!
        assertEquals("Europe/Madrid", zone.property("TZID")?.value)
        assertEquals(
            mapOf(
                "DAYLIGHT" to
                    listOf("19700329T020000", "FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU", "+0100", "+0200"),
                "STANDARD" to
                    listOf("19701025T030000", "FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU", "+0200", "+0100")
            ),
            blocks("Europe/Madrid")
        )
    }

    @Test
    fun `American rules use the n-th Sunday and negative offsets`() {
        val blocks = blocks("America/New_York")
        assertEquals("FREQ=YEARLY;BYMONTH=3;BYDAY=2SU", blocks["DAYLIGHT"]!![1])
        assertEquals("FREQ=YEARLY;BYMONTH=11;BYDAY=1SU", blocks["STANDARD"]!![1])
        assertEquals("-0400", blocks["STANDARD"]!![2])
    }

    @Test
    fun `other weekday rules list the week of days`() {
        assertEquals(
            "FREQ=YEARLY;BYMONTH=9;BYMONTHDAY=2,3,4,5,6,7,8;BYDAY=SU",
            blocks("America/Santiago")["DAYLIGHT"]!![1]
        )
    }

    @Test
    fun `zones without summer time have one block`() {
        assertEquals(
            mapOf("STANDARD" to listOf("19700101T000000", null, "+0900", "+0900")),
            blocks("Asia/Tokyo")
        )
        assertEquals("+0530", blocks("Asia/Kolkata")["STANDARD"]!![2])
    }

    @Test
    fun `unknown zones have none`() {
        assertNull(VTimeZones.of("Mars/Olympus"))
    }
}
