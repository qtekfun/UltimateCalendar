// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.widget

/** The system's tonal colors (Android 12+), as the widget needs them; ARGB. */
data class DynamicTones(
    val neutral10: Int,
    val neutral100: Int,
    val neutral900: Int,
    val neutralVariant200: Int,
    val neutralVariant700: Int,
    val accent0: Int,
    val accent200: Int,
    val accent600: Int,
    val accent800: Int
)

/** The colors of a widget (ARGB), light, dark or AMOLED, from the wallpaper when it can. */
data class WidgetPalette(
    val background: Int,
    val text: Int,
    val secondaryText: Int,
    val accent: Int,
    val onAccent: Int,
    val dark: Boolean
) {
    companion object {
        private const val BLACK = 0xFF000000.toInt()
        private val FALLBACK_LIGHT = WidgetPalette(
            background = 0xFFFAF9FD.toInt(),
            text = 0xFF1B1B1F.toInt(),
            secondaryText = 0xFF44474F.toInt(),
            accent = 0xFF0B63CE.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            dark = false
        )
        private val FALLBACK_DARK = WidgetPalette(
            background = 0xFF121316.toInt(),
            text = 0xFFE3E2E6.toInt(),
            secondaryText = 0xFFC4C6D0.toInt(),
            accent = 0xFFA8C8FF.toInt(),
            onAccent = 0xFF002F65.toInt(),
            dark = true
        )

        /**
         * The palette for the user's theme. [dark] is whether the theme is dark (the user's
         * choice, else the system's); [amoled] only matters then. [tones] are the system's
         * dynamic colors, null where there are none or the user switched them off.
         */
        fun of(dark: Boolean, amoled: Boolean, tones: DynamicTones?): WidgetPalette {
            val base = when {
                tones == null -> if (dark) FALLBACK_DARK else FALLBACK_LIGHT

                dark -> WidgetPalette(
                    tones.neutral900,
                    tones.neutral100,
                    tones.neutralVariant200,
                    tones.accent200,
                    tones.accent800,
                    dark = true
                )

                else -> WidgetPalette(
                    tones.neutral10,
                    tones.neutral900,
                    tones.neutralVariant700,
                    tones.accent600,
                    tones.accent0,
                    dark = false
                )
            }
            return if (dark && amoled) base.copy(background = BLACK) else base
        }
    }
}
