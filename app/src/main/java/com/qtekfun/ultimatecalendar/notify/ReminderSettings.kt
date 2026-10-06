// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindOption
import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules
import java.time.LocalTime

/**
 * What the reminders read from the settings (RF-08, RF-10). [allDayTime] is when all-day events
 * remind, [missedWindowHours] how far back a missed reminder is brought back (0 = never),
 * [alarmClock] sets reminders like an alarm clock and [robustMode] keeps a service running.
 */
data class ReminderSettings(
    val allDayTime: LocalTime = LocalTime.of(DEFAULT_ALL_DAY_HOUR, 0),
    val missedWindowHours: Int = DEFAULT_MISSED_WINDOW_HOURS,
    val alarmClock: Boolean = false,
    val robustMode: Boolean = false,
    /** When unanswered invitations remind again (T40); off by default. */
    val reRemind: ReRemindOption = ReRemindOption.OFF,
    /** What events that use "the calendar's default reminders" get (see `DefaultReminders`). */
    val defaultReminders: List<Int> = SettingsRules.DEFAULT_REMINDERS,
    val defaultAllDayReminders: List<Int> = SettingsRules.DEFAULT_ALL_DAY_REMINDERS
) {
    private companion object {
        const val DEFAULT_ALL_DAY_HOUR = 9
        const val DEFAULT_MISSED_WINDOW_HOURS = 24
    }
}
