// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings

import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules
import com.qtekfun.ultimatecalendar.notify.ReminderSettings
import com.qtekfun.ultimatecalendar.notify.ReminderSettingsSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the reminders read from the settings: the part of [AppSettings] they care about. */
@Singleton
class RepositoryReminderSettings @Inject constructor(private val repository: SettingsRepository) :
    ReminderSettingsSource {
    override val settings: Flow<ReminderSettings> = repository.settings.map {
        it.toReminderSettings()
    }

    override fun setRobustMode(on: Boolean) = repository.update { it.copy(robustMode = on) }
}

internal fun AppSettings.toReminderSettings() = ReminderSettings(
    allDayTime = SettingsRules.allDayTime(allDayMinute),
    missedWindowHours = missedWindowHours,
    alarmClock = alarmClock,
    robustMode = robustMode,
    reRemind = reRemind
)
