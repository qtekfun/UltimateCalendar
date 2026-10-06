// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.widget

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WidgetRefreshTimesTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun `the next midnight is the start of the next day in the zone`() {
        val now = Instant.parse("2026-10-06T10:15:00Z")

        assertEquals(
            Instant.parse("2026-10-06T22:00:00Z"),
            WidgetRefreshTimes.nextMidnight(now, madrid)
        )
    }

    @Test
    fun `just after midnight the next one is a day later`() {
        val now = Instant.parse("2026-10-05T22:00:00Z")

        assertEquals(
            Instant.parse("2026-10-06T22:00:00Z"),
            WidgetRefreshTimes.nextMidnight(now, madrid)
        )
    }

    @Test
    fun `the day a clock change shortens or lengthens is honoured`() {
        // 2026-03-29 has 23 hours and 2026-10-25 has 25 in Madrid.
        val springStart = Instant.parse("2026-03-28T23:00:00Z")
        val autumnStart = Instant.parse("2026-10-24T22:00:00Z")

        val spring = WidgetRefreshTimes.nextMidnight(springStart.plusSeconds(1800), madrid)
        val autumn = WidgetRefreshTimes.nextMidnight(autumnStart.plusSeconds(1800), madrid)

        assertEquals(Duration.ofHours(23), Duration.between(springStart, spring))
        assertEquals(Duration.ofHours(25), Duration.between(autumnStart, autumn))
    }

    @Test
    fun `a zone ahead of UTC moves the day boundary`() {
        val now = Instant.parse("2026-10-06T23:00:00Z")

        assertEquals(
            Instant.parse("2026-10-07T11:00:00Z"),
            WidgetRefreshTimes.nextMidnight(now, ZoneId.of("Pacific/Auckland"))
        )
    }
}
