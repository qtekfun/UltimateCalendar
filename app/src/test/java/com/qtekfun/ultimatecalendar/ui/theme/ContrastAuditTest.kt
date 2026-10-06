// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WCAG 2.x audit (T26) over the real theme schemes and a grid of calendar colors: text 4.5:1,
 * non-text 3:1, in the light, dark and AMOLED themes. The wallpaper (dynamic) colors cannot be
 * known here; Material generates them with accessible pairs.
 */
class ContrastAuditTest {
    private val schemes: Map<String, ColorScheme> = mapOf(
        "light" to LightColors,
        "dark" to DarkColors,
        "amoled" to DarkColors.toAmoled()
    )

    /** Hue every 30 degrees, three saturations, seven lightnesses, plus grays. */
    private val colors: List<Int> = buildList {
        for (hue in 0 until FULL_TURN step HUE_STEP) {
            for (saturation in listOf(0.35, 0.7, 1.0)) {
                for (lightness in listOf(0.1, 0.25, 0.4, 0.5, 0.6, 0.75, 0.9)) {
                    add(hsl(hue.toDouble(), saturation, lightness))
                }
            }
        }
        for (gray in 0..255 step GRAY_STEP) {
            add((0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray)
        }
    }

    /** Every scheme with every grid color and every display state, for the checks below. */
    private fun forEachChip(check: (Chip) -> Unit) {
        for ((name, scheme) in schemes) {
            for (color in colors) {
                for (display in EventDisplay.entries) {
                    check(
                        Chip(
                            name,
                            scheme,
                            color,
                            display,
                            chip(
                                color,
                                display,
                                scheme,
                                name != "light"
                            )
                        )
                    )
                }
            }
        }
    }

    private class Chip(
        val theme: String,
        val scheme: ColorScheme,
        val color: Int,
        val display: EventDisplay,
        val colors: EventChipColors
    ) {
        val label get() = "$display $theme ${color.toUInt().toString(HEX)}"
    }

    private fun contrast(a: Color, b: Color) = ColorMath.contrast(a.toArgb(), b.toArgb())

    private fun chip(color: Int, display: EventDisplay, scheme: ColorScheme, dark: Boolean) =
        eventChipColors(
            color,
            display,
            dark,
            scheme.surface.toArgb(),
            scheme.onSurface.toArgb(),
            scheme.onSurfaceVariant.toArgb()
        )

    @Test
    fun `the grid covers a few hundred colors`() {
        assertTrue(colors.size > MIN_COLORS, "${colors.size}")
    }

    @Test
    fun `event text reaches 4_5 to 1 for every color, state and theme`() = forEachChip { chip ->
        val behind = if (chip.colors.container.alpha ==
            0f
        ) {
            chip.scheme.surface
        } else {
            chip.colors.container
        }
        val ratio = contrast(chip.colors.content, behind)
        assertTrue(ratio >= ColorMath.TEXT_CONTRAST, "text ${chip.label} = $ratio")
    }

    @Test
    fun `outlines of tentative and pending events reach 3 to 1 against the surface`() =
        forEachChip { chip ->
            if (chip.display == EventDisplay.TENTATIVE || chip.display == EventDisplay.PENDING) {
                val border = requireNotNull(chip.colors.border)
                val ratio = contrast(border, chip.scheme.surface)
                assertTrue(ratio >= ColorMath.GRAPHIC_CONTRAST, "border ${chip.label} = $ratio")
            }
        }

    @Test
    fun `a pending event is outlined and a declined one struck, never only recolored`() =
        forEachChip { chip ->
            when (chip.display) {
                EventDisplay.PENDING -> assertTrue(chip.colors.border != null, chip.label)
                EventDisplay.DECLINED -> assertTrue(chip.colors.strikeThrough, chip.label)
                EventDisplay.CONFIRMED -> assertEquals(null, chip.colors.border, chip.label)
                EventDisplay.TENTATIVE -> assertTrue(chip.colors.border != null, chip.label)
            }
        }

    @Test
    fun `the dot of a month event reaches 3 to 1 against the surface`() {
        schemes.forEach { (name, scheme) ->
            colors.forEach { color ->
                val dot = ColorMath.ensureContrast(
                    color,
                    scheme.surface.toArgb(),
                    ColorMath.GRAPHIC_CONTRAST
                )
                val ratio = ColorMath.contrast(dot, scheme.surface.toArgb())
                assertTrue(
                    ratio >= ColorMath.GRAPHIC_CONTRAST,
                    "dot $name ${color.toUInt()} = $ratio"
                )
            }
        }
    }

    @Test
    fun `body text pairs of the theme reach AA in every theme`() {
        schemes.forEach { (name, s) ->
            val pairs = listOf(
                "onSurface/surface" to (s.onSurface to s.surface),
                "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
                "onSurface/container" to (s.onSurface to s.surfaceContainer),
                "onSurfaceVariant/container" to (s.onSurfaceVariant to s.surfaceContainer),
                "onPrimary/primary" to (s.onPrimary to s.primary),
                "onPrimaryContainer/primaryContainer" to
                    (s.onPrimaryContainer to s.primaryContainer),
                "onSecondaryContainer/secondaryContainer" to
                    (s.onSecondaryContainer to s.secondaryContainer),
                "primary/surface" to (s.primary to s.surface),
                "error/surface" to (s.error to s.surface)
            )
            pairs.forEach { (label, pair) ->
                val ratio = contrast(pair.first, pair.second)
                assertTrue(ratio >= ColorMath.TEXT_CONTRAST, "$label $name = $ratio")
            }
        }
    }

    @Test
    fun `dimmed day numbers of the neighbouring months stay legible`() {
        // DayBadge draws them in onSurfaceVariant at 80 % alpha.
        schemes.forEach { (name, s) ->
            val faded = ColorMath.blend(
                s.surface.toArgb(),
                s.onSurfaceVariant.toArgb(),
                DIMMED_ALPHA
            )
            val ratio = ColorMath.contrast(faded, s.surface.toArgb())
            assertTrue(ratio >= ColorMath.TEXT_CONTRAST, "dimmed $name = $ratio")
        }
    }

    @Test
    fun `the current-time line and the selection ring are visible against the surface`() {
        schemes.forEach { (name, s) ->
            // The now line and its dot are drawn in the error color; today's badge in primary.
            assertTrue(contrast(s.error, s.surface) >= ColorMath.GRAPHIC_CONTRAST, "now $name")
            assertTrue(contrast(s.primary, s.surface) >= ColorMath.GRAPHIC_CONTRAST, "today $name")
            // Outline-colored borders (fields, outlined buttons) are controls: 3:1.
            assertTrue(
                contrast(s.outline, s.surface) >= ColorMath.GRAPHIC_CONTRAST,
                "outline $name"
            )
        }
    }

    @Test
    fun `the selected day badge is told apart from the surface by more than color`() {
        // It is also read as "selected" (semantics) and its number is bold; the fill alone may
        // be faint, so the text on it must at least stay AA.
        schemes.forEach { (name, s) ->
            assertTrue(
                contrast(s.onPrimaryContainer, s.primaryContainer) >= ColorMath.TEXT_CONTRAST,
                "selected badge $name"
            )
        }
    }

    private fun hsl(hue: Double, saturation: Double, lightness: Double): Int {
        val c = (1 - abs(2 * lightness - 1)) * saturation
        val x = c * (1 - abs((hue / SECTOR) % 2 - 1))
        val m = lightness - c / 2
        val (r, g, b) = when ((hue / SECTOR).toInt()) {
            0 -> Triple(c, x, 0.0)
            1 -> Triple(x, c, 0.0)
            2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c)
            4 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        fun channel(v: Double) = ((v + m) * CHANNEL_MAX + HALF).toInt()
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    private companion object {
        const val FULL_TURN = 360
        const val HUE_STEP = 30
        const val GRAY_STEP = 17
        const val SECTOR = 60.0
        const val CHANNEL_MAX = 255.0
        const val HALF = 0.5
        const val MIN_COLORS = 200
        const val HEX = 16
        const val DIMMED_ALPHA = 0.8f
    }
}
