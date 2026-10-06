// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.month

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MonthDensityTest {
    @Test
    fun `lanes are whole lanes below the header`() {
        assertEquals(3, MonthDensity.lanes(cellHeight = 100f, headerHeight = 36f, laneHeight = 20f))
        assertEquals(1, MonthDensity.lanes(cellHeight = 60f, headerHeight = 36f, laneHeight = 20f))
    }

    @Test
    fun `a cell shorter than its header has no lanes`() {
        assertEquals(0, MonthDensity.lanes(cellHeight = 20f, headerHeight = 36f, laneHeight = 20f))
    }

    @Test
    fun `a lane needs some height`() {
        assertThrows(IllegalArgumentException::class.java) { MonthDensity.lanes(100f, 0f, 0f) }
    }

    @Test
    fun `fewer than two lanes make a compact cell`() {
        assertTrue(MonthDensity.isCompact(0))
        assertTrue(MonthDensity.isCompact(1))
        assertFalse(MonthDensity.isCompact(2))
    }
}
