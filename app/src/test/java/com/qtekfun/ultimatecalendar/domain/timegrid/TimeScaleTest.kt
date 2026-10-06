// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TimeScaleTest {
    private val scale = TimeScale(hourHeight = 60f)

    @Test
    fun `minutes map to offsets in proportion`() {
        assertEquals(0f, scale.offsetOf(0))
        assertEquals(90f, scale.offsetOf(90))
        assertEquals(1440f, scale.totalHeight)
    }

    @Test
    fun `the scale follows the hour height`() {
        val tall = TimeScale(48f)

        assertEquals(24f, tall.offsetOf(30))
        assertEquals(24 * 48f, tall.totalHeight)
    }

    @Test
    fun `a block is as tall as its time, but never under the minimum`() {
        assertEquals(45f, scale.heightOf(60, 105))
        assertEquals(30f, scale.heightOf(60, 60, minHeight = 30f))
        assertEquals(45f, scale.heightOf(60, 105, minHeight = 30f))
    }

    @Test
    fun `a position snaps down to the step`() {
        assertEquals(0, scale.minuteAt(0f))
        assertEquals(0, scale.minuteAt(29f))
        assertEquals(30, scale.minuteAt(30f))
        assertEquals(9 * 60 + 30, scale.minuteAt(9 * 60 + 46f))
        assertEquals(9 * 60 + 45, scale.minuteAt(9 * 60 + 46f, snapMinutes = 15))
        assertEquals(9 * 60 + 46, scale.minuteAt(9 * 60 + 46f, snapMinutes = 1))
    }

    @Test
    fun `the step is measured on the scale in use`() {
        // At 120 per hour, 150 units from the top is 75 minutes in.
        assertEquals(60, TimeScale(120f).minuteAt(150f))
        assertEquals(75, TimeScale(120f).minuteAt(150f, snapMinutes = 15))
    }

    @Test
    fun `a position outside the grid stays inside the day`() {
        assertEquals(0, scale.minuteAt(-50f))
        assertEquals(23 * 60 + 30, scale.minuteAt(5000f))
        assertEquals(23 * 60 + 45, scale.minuteAt(5000f, snapMinutes = 15))
    }

    @Test
    fun `a grid needs a height and a step within the hour`() {
        assertThrows<IllegalArgumentException> { TimeScale(0f) }
        assertThrows<IllegalArgumentException> { scale.minuteAt(10f, snapMinutes = 0) }
        assertThrows<IllegalArgumentException> { scale.minuteAt(10f, snapMinutes = 61) }
    }
}
