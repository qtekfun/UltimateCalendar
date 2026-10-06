// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.theme

import com.qtekfun.ultimatecalendar.data.settings.AppSettings
import com.qtekfun.ultimatecalendar.data.settings.ThemeMode

/** What the user picks in Settings (RF-10); the defaults follow the system. */
data class ThemeOptions(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = true
)

/** The look the user chose in Settings. */
fun AppSettings.toThemeOptions() = ThemeOptions(theme, amoled, dynamicColor)
