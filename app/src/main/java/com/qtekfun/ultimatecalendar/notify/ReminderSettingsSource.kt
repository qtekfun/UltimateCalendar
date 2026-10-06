// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import kotlinx.coroutines.flow.Flow

/**
 * Where the reminders find their settings. The settings screen (RF-10) will implement it; until
 * then [PreferencesReminderSettings] keeps the defaults and the robust mode switch.
 */
interface ReminderSettingsSource {
    val settings: Flow<ReminderSettings>

    fun setRobustMode(on: Boolean)
}
