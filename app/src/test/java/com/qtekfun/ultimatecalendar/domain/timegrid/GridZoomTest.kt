// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GridZoomTest {
    @Test
    fun `the zoom stays between its limits`() {
        assertEquals(1.7f, GridZoom.clamp(1.7f))
        assertEquals(GridZoom.MIN, GridZoom.clamp(0.1f))
        assertEquals(GridZoom.MAX, GridZoom.clamp(9f))
    }

    @Test
    fun `a value that is not a number goes back to the default`() {
        assertEquals(GridZoom.DEFAULT, GridZoom.clamp(Float.NaN))
        assertEquals(GridZoom.DEFAULT, GridZoom.clamp(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `zooming in keeps the time under the fingers where it was`() {
        // 64 per hour, scrolled to 7:00 (448), fingers 128 below the top: 9:00 is under them.
        val scroll = GridZoom.scrollKeeping(scroll = 448f, focus = 128f, from = 1f, to = 2f)
        // 128 per hour now: 9:00 is at 1152, and it must still be 128 below the top.
        assertEquals(1152f - 128f, scroll)
    }

    @Test
    fun `zooming out keeps it too`() {
        val scroll = GridZoom.scrollKeeping(scroll = 1024f, focus = 128f, from = 2f, to = 1f)
        assertEquals(448f, scroll)
    }

    @Test
    fun `the scroll never goes above the top of the grid`() {
        assertEquals(0f, GridZoom.scrollKeeping(scroll = 0f, focus = 300f, from = 2f, to = 0.5f))
    }

    @Test
    fun `the same zoom leaves the scroll alone`() {
        val scroll = GridZoom.scrollKeeping(scroll = 500f, focus = 40f, from = 1.3f, to = 1.3f)
        assertEquals(500f, scroll)
    }
}
