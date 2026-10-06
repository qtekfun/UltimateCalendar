// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetPaletteTest {
    private val tones = DynamicTones(1, 2, 3, 4, 5, 6, 7, 8, 9)

    @Test
    fun `without dynamic colors light and dark use their fixed palettes`() {
        val light = WidgetPalette.of(dark = false, amoled = false, tones = null)
        val dark = WidgetPalette.of(dark = true, amoled = false, tones = null)

        assertFalse(light.dark)
        assertTrue(dark.dark)
        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.text, dark.text)
    }

    @Test
    fun `dynamic light colors come from the system's light tones`() {
        val palette = WidgetPalette.of(dark = false, amoled = true, tones = tones)

        assertEquals(WidgetPalette(1, 3, 5, 8, 6, dark = false), palette)
    }

    @Test
    fun `dynamic dark colors come from the system's dark tones`() {
        val palette = WidgetPalette.of(dark = true, amoled = false, tones = tones)

        assertEquals(WidgetPalette(3, 2, 4, 7, 9, dark = true), palette)
    }

    @Test
    fun `AMOLED turns only a dark background pure black, dynamic or not`() {
        val black = 0xFF000000.toInt()

        assertEquals(black, WidgetPalette.of(true, true, null).background)
        assertEquals(black, WidgetPalette.of(true, true, tones).background)
        assertEquals(2, WidgetPalette.of(true, true, tones).text)
        assertNotEquals(black, WidgetPalette.of(false, true, null).background)
    }
}
