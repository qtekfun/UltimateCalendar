// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.theme

import kotlin.math.pow

/**
 * Pure color arithmetic on opaque ARGB ints (WCAG 2.x), kept free of Compose so it is unit
 * tested. Used by the event color system to pick readable text and legible borders.
 */
object ColorMath {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val CHANNEL_MAX = 255.0
    private const val LINEAR_CUTOFF = 0.03928
    private const val LINEAR_SLOPE = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_DIVISOR = 1.055
    private const val GAMMA = 2.4
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val FLARE = 0.05
    private const val STEPS = 20
    private const val MID_LUMINANCE = 0.5
    private const val BYTE = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    /** WCAG AA for normal text. */
    const val TEXT_CONTRAST = 4.5

    /** WCAG AA for graphics and borders. */
    const val GRAPHIC_CONTRAST = 3.0

    /** Relative luminance, 0 (black) to 1 (white); alpha is ignored. */
    fun luminance(argb: Int): Double {
        fun linear(shift: Int): Double {
            val c = ((argb shr shift) and BYTE) / CHANNEL_MAX
            return if (c <= LINEAR_CUTOFF) {
                c / LINEAR_SLOPE
            } else {
                ((c + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA)
            }
        }
        return RED_WEIGHT * linear(RED_SHIFT) +
            GREEN_WEIGHT * linear(GREEN_SHIFT) +
            BLUE_WEIGHT * linear(0)
    }

    /** Contrast ratio between two colors, 1 (none) to 21 (black on white). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + FLARE) / (minOf(la, lb) + FLARE)
    }

    /** [from] moved [fraction] (0..1) of the way to [to], opaque. */
    fun blend(from: Int, to: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        fun mix(shift: Int): Int {
            val a = (from shr shift) and BYTE
            val b = (to shr shift) and BYTE
            return (a + (b - a) * f + 0.5f).toInt().coerceIn(0, BYTE) shl shift
        }
        return BLACK or mix(RED_SHIFT) or mix(GREEN_SHIFT) or mix(0)
    }

    /**
     * White or black, whichever reads better on [fill]. Against pure black and white the best
     * of the two always reaches [TEXT_CONTRAST], whatever the fill.
     */
    fun onColor(fill: Int): Int =
        if (contrast(fill, WHITE) >= contrast(fill, BLACK)) WHITE else BLACK

    /**
     * [color] pushed toward black (on a light [background]) or white (on a dark one) just until
     * it reaches [minContrast], so it keeps its hue as long as it can.
     */
    fun ensureContrast(color: Int, background: Int, minContrast: Double = TEXT_CONTRAST): Int {
        val target = if (luminance(background) > MID_LUMINANCE) BLACK else WHITE
        return (0..STEPS)
            .asSequence()
            .map { step -> blend(color, target, step / STEPS.toFloat()) }
            .firstOrNull { contrast(it, background) >= minContrast }
            ?: target
    }
}
