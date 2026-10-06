// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ColorMathTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    /** A spread of hues, saturations and lightnesses, plus Google Calendar's own palette. */
    private val palette: List<Int> = buildList {
        for (r in 0..255 step 51) {
            for (g in 0..255 step 51) {
                for (b in 0..255 step 51) add((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        addAll(
            listOf(0xFFD50000, 0xFFE67C73, 0xFFF4511E, 0xFFF6BF26, 0xFF33B679, 0xFF0B8043)
                .map { it.toInt() }
        )
    }

    @Test
    fun `black on white is the maximum contrast and a color against itself the minimum`() {
        assertEquals(21.0, ColorMath.contrast(black, white), 0.001)
        assertEquals(1.0, ColorMath.contrast(white, white), 0.001)
        assertEquals(ColorMath.contrast(black, white), ColorMath.contrast(white, black), 0.0)
    }

    @Test
    fun `luminance of the primaries follows the WCAG weights`() {
        assertEquals(0.2126, ColorMath.luminance(0xFFFF0000.toInt()), 0.0001)
        assertEquals(0.7152, ColorMath.luminance(0xFF00FF00.toInt()), 0.0001)
        assertEquals(0.0722, ColorMath.luminance(0xFF0000FF.toInt()), 0.0001)
    }

    @Test
    fun `text on any fill reaches AA`() {
        palette.forEach { fill ->
            val text = ColorMath.onColor(fill)
            assertTrue(
                ColorMath.contrast(fill, text) >= ColorMath.TEXT_CONTRAST,
                "fill ${fill.toUInt().toString(16)}"
            )
        }
    }

    @Test
    fun `yellow gets dark text and navy gets white text`() {
        assertEquals(black, ColorMath.onColor(0xFFF6BF26.toInt()))
        assertEquals(white, ColorMath.onColor(0xFF1A237E.toInt()))
    }

    @Test
    fun `blend moves between the colors and clamps the fraction`() {
        assertEquals(0xFF808080.toInt(), ColorMath.blend(black, white, 0.5f))
        assertEquals(white, ColorMath.blend(black, white, 2f))
        assertEquals(black, ColorMath.blend(black, white, -1f))
        assertEquals(0xFFFFFFFF.toInt(), ColorMath.blend(0x00FFFFFF, white, 0.3f))
    }

    @Test
    fun `ensureContrast keeps a legible color and fixes an illegible one`() {
        val navy = 0xFF1A237E.toInt()
        assertEquals(navy, ColorMath.ensureContrast(navy, white))
        val yellow = 0xFFF6BF26.toInt()
        val fixed = ColorMath.ensureContrast(yellow, white)
        assertTrue(ColorMath.contrast(fixed, white) >= ColorMath.TEXT_CONTRAST)
        // On a dark background it moves the other way.
        val onDark = ColorMath.ensureContrast(navy, 0xFF121316.toInt())
        assertTrue(ColorMath.contrast(onDark, 0xFF121316.toInt()) >= ColorMath.TEXT_CONTRAST)
        assertTrue(ColorMath.luminance(onDark) > ColorMath.luminance(navy))
    }

    @Test
    fun `ensureContrast gives up at the extreme when the target cannot be met`() {
        // An impossible requirement ends at the extreme, black on a light background.
        assertEquals(black, ColorMath.ensureContrast(0xFFFFFFFF.toInt(), white, 22.0))
    }

    @Test
    fun `chip text and borders are legible in light and dark, for every state`() {
        listOf(false, true).forEach { dark ->
            palette.forEach { color -> EventDisplay.entries.forEach { checkChip(color, it, dark) } }
        }
    }

    private fun checkChip(color: Int, display: EventDisplay, dark: Boolean) {
        val surface = if (dark) 0xFF121316.toInt() else 0xFFFAF9FD.toInt()
        val onSurface = if (dark) 0xFFE3E2E6.toInt() else 0xFF1B1B1F.toInt()
        val variant = if (dark) 0xFFC4C6D0.toInt() else 0xFF44474E.toInt()
        val chip = eventChipColors(color, display, dark, surface, onSurface, variant)
        val fill = chip.container.toArgb()
        val background = if (fill ushr 24 == 0) surface else fill
        val label = "$display dark=$dark ${color.toUInt().toString(16)}"
        assertTrue(
            ColorMath.contrast(chip.content.toArgb(), background) >= ColorMath.TEXT_CONTRAST,
            "text $label"
        )
        chip.border?.let {
            assertTrue(
                ColorMath.contrast(it.toArgb(), surface) >= ColorMath.GRAPHIC_CONTRAST,
                "border $label"
            )
        }
    }

    @Test
    fun `each state has its own look`() {
        val blue = 0xFF1A73E8.toInt()
        val surface = 0xFFFAF9FD.toInt()
        fun chip(display: EventDisplay, dark: Boolean = false) =
            eventChipColors(blue, display, dark, surface, black, 0xFF44474E.toInt())

        val confirmed = chip(EventDisplay.CONFIRMED)
        assertEquals(blue, confirmed.container.toArgb())
        assertEquals(null, confirmed.border)
        assertFalse(confirmed.strikeThrough)

        assertEquals(surface, chip(EventDisplay.PENDING).container.toArgb())
        assertTrue(chip(EventDisplay.PENDING).border != null)

        assertTrue(chip(EventDisplay.TENTATIVE).border != null)
        assertTrue(chip(EventDisplay.DECLINED).strikeThrough)
        assertEquals(null, chip(EventDisplay.DECLINED).border)

        // The dark theme tones a solid fill down toward the surface.
        val dark = chip(EventDisplay.CONFIRMED, dark = true)
        assertTrue(dark.container.toArgb() != blue)
    }

    @Test
    fun `the user's answer picks the display state`() {
        assertEquals(EventDisplay.CONFIRMED, EventDisplay.of(null))
        assertEquals(EventDisplay.CONFIRMED, EventDisplay.of(AttendeeStatus.ACCEPTED))
        assertEquals(EventDisplay.TENTATIVE, EventDisplay.of(AttendeeStatus.TENTATIVE))
        assertEquals(EventDisplay.PENDING, EventDisplay.of(AttendeeStatus.NEEDS_ACTION))
        assertEquals(EventDisplay.DECLINED, EventDisplay.of(AttendeeStatus.DECLINED))
    }
}
