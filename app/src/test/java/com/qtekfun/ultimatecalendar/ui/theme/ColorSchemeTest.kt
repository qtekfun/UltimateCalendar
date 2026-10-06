// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.qtekfun.ultimatecalendar.data.settings.ThemeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ColorSchemeTest {
    private val wallpaperLight = lightColorScheme(primary = Color.Green)
    private val wallpaperDark = darkColorScheme(primary = Color.Magenta)

    @Test
    fun `system mode follows the system`() {
        assertSame(LightColors, scheme(ThemeOptions(dynamicColor = false), systemDark = false))
        assertSame(DarkColors, scheme(ThemeOptions(dynamicColor = false), systemDark = true))
    }

    @Test
    fun `a forced mode ignores the system`() {
        val light = ThemeOptions(mode = ThemeMode.LIGHT, dynamicColor = false)
        val dark = ThemeOptions(mode = ThemeMode.DARK, dynamicColor = false)
        assertSame(LightColors, scheme(light, systemDark = true))
        assertSame(DarkColors, scheme(dark, systemDark = false))
    }

    @Test
    fun `wallpaper colors are used when enabled and available`() {
        assertSame(wallpaperLight, scheme(ThemeOptions(), systemDark = false))
        assertSame(wallpaperDark, scheme(ThemeOptions(), systemDark = true))
    }

    @Test
    fun `without wallpaper colors the fallback palette is used`() {
        val scheme = colorSchemeFor(ThemeOptions(), false, dynamicLight = null, dynamicDark = null)
        assertSame(LightColors, scheme)
    }

    @Test
    fun `amoled turns the dark background pure black`() {
        val scheme = scheme(ThemeOptions(mode = ThemeMode.DARK, amoled = true), systemDark = false)
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
        assertEquals(AmoledContainer, scheme.surfaceContainer)
        assertEquals(wallpaperDark.primary, scheme.primary)
    }

    @Test
    fun `amoled does nothing in light mode`() {
        val options = ThemeOptions(mode = ThemeMode.LIGHT, amoled = true)
        assertSame(wallpaperLight, scheme(options, systemDark = true))
    }

    private fun scheme(options: ThemeOptions, systemDark: Boolean) =
        colorSchemeFor(options, systemDark, wallpaperLight, wallpaperDark)
}
