// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import kotlinx.coroutines.flow.Flow

/**
 * Where the reminders find their settings: the settings repository (RF-10), through
 * [com.qtekfun.ultimatecalendar.data.settings.RepositoryReminderSettings].
 */
interface ReminderSettingsSource {
    val settings: Flow<ReminderSettings>

    fun setRobustMode(on: Boolean)
}
