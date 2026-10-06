// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class IcsDurationTest {
    @Test
    fun `parses every part and the sign`() {
        assertEquals(-900L, IcsDuration.parse("-PT15M"))
        assertEquals(604_800L, IcsDuration.parse("P1W"))
        assertEquals(93_784L, IcsDuration.parse("+P1DT2H3M4S"))
        assertEquals(-86_400L, IcsDuration.parse(" -p1d "))
        assertEquals(0L, IcsDuration.parse("PT0S"))
    }

    @Test
    fun `rejects what is not a duration`() {
        assertNull(IcsDuration.parse("P"))
        assertNull(IcsDuration.parse("PT"))
        assertNull(IcsDuration.parse("15 minutes"))
    }

    @Test
    fun `formats like other clients do`() {
        assertEquals("-PT15M", IcsDuration.format(-900))
        assertEquals("-P1W", IcsDuration.format(-604_800))
        assertEquals("P1DT2H3M4S", IcsDuration.format(93_784))
        assertEquals("-P2D", IcsDuration.format(-172_800))
        assertEquals("PT1H", IcsDuration.format(3_600))
        assertEquals("PT0S", IcsDuration.format(0))
    }

    @Test
    fun `format and parse are inverse`() {
        listOf(-1L, -59L, -3_601L, 90_061L, -1_209_600L).forEach {
            assertEquals(it, IcsDuration.parse(IcsDuration.format(it)))
        }
    }
}
