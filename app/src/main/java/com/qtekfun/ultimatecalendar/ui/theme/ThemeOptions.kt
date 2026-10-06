// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.theme

import com.qtekfun.ultimatecalendar.data.settings.ThemeMode

/** What the user picks in Settings (T22); the defaults follow the system. */
data class ThemeOptions(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = true
)
