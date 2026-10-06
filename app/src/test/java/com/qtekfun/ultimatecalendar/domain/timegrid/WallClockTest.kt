// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WallClockTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun `an ordinary time is itself`() {
        assertEquals(
            Instant.parse("2026-03-11T08:00:00Z"),
            WallClock.resolve(LocalDateTime.of(2026, 3, 11, 9, 0), madrid)
        )
    }

    @Test
    fun `a time that is skipped moves forward by the length of the gap`() {
        // Clocks go from 02:00 to 03:00 on 2026-03-29.
        assertEquals(
            Instant.parse("2026-03-29T01:00:00Z"),
            WallClock.resolve(LocalDateTime.of(2026, 3, 29, 2, 0), madrid)
        )
        assertEquals(
            Instant.parse("2026-03-29T01:45:00Z"),
            WallClock.resolve(LocalDateTime.of(2026, 3, 29, 2, 45), madrid)
        )
    }

    @Test
    fun `a time that happens twice is the first time`() {
        // Clocks go back from 03:00 to 02:00 on 2026-10-25.
        assertEquals(
            Instant.parse("2026-10-25T00:30:00Z"),
            WallClock.resolve(LocalDateTime.of(2026, 10, 25, 2, 30), madrid)
        )
    }

    @Test
    fun `a gap of half an hour is honoured`() {
        val lordHowe = ZoneId.of("Australia/Lord_Howe")
        // Clocks go from 02:00 to 02:30 on 2026-10-04.
        assertEquals(
            Instant.parse("2026-10-03T15:40:00Z"),
            WallClock.resolve(LocalDateTime.of(2026, 10, 4, 2, 10), lordHowe)
        )
    }
}
