// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.theme

import com.qtekfun.ultimatecalendar.data.settings.AppSettings
import com.qtekfun.ultimatecalendar.data.settings.ThemeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemeOptionsTest {
    @Test
    fun `the default settings give the default look`() {
        assertEquals(ThemeOptions(), AppSettings().toThemeOptions())
    }

    @Test
    fun `the look follows the settings`() {
        val settings = AppSettings(theme = ThemeMode.DARK, amoled = true, dynamicColor = false)
        assertEquals(
            ThemeOptions(ThemeMode.DARK, amoled = true, dynamicColor = false),
            settings.toThemeOptions()
        )
    }
}
