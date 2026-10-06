// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings

import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindOption
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.settings.FirstDayOfWeek
import com.qtekfun.ultimatecalendar.domain.settings.InitialView
import com.qtekfun.ultimatecalendar.domain.settings.InviteCheckInterval
import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules

/** Preferences of this device (RF-10). Use [sanitized] on anything that did not come from the UI. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Pure black backgrounds in dark mode, for OLED screens. */
    val amoled: Boolean = false,
    /** Material You colors from the wallpaper (Android 12+). */
    val dynamicColor: Boolean = true,
    val firstDayOfWeek: FirstDayOfWeek = FirstDayOfWeek.LOCALE,
    val initialView: InitialView = InitialView.WEEK,
    /** Where new events go; null: the first calendar that can be written. */
    val defaultCalendar: CalendarId? = null,
    val defaultDurationMinutes: Int = SettingsRules.DEFAULT_DURATION_MINUTES,
    /** Reminders given to a new timed event, in minutes before it starts. */
    val defaultReminders: List<Int> = SettingsRules.DEFAULT_REMINDERS,
    /** Reminders given to a new all-day event, in minutes before [allDayMinute] of its day. */
    val defaultAllDayReminders: List<Int> = SettingsRules.DEFAULT_ALL_DAY_REMINDERS,
    val inviteCheck: InviteCheckInterval = InviteCheckInterval.EVERY_15,
    /** Addresses that are the user's own, besides the accounts' (lower case). */
    val ownEmails: List<String> = emptyList(),
    /** Tell when the organizer moves an invitation (RF-07); off by default. */
    val notifyChanges: Boolean = false,
    /** Tell when the organizer cancels an invitation (RF-07); off by default. */
    val notifyCancellations: Boolean = false,
    /** Remind again about invitations not answered yet (T40); off by default. */
    val reRemind: ReRemindOption = ReRemindOption.OFF,
    /** How far back reminders the system kept from showing are brought back; 0: never. */
    val missedWindowHours: Int = SettingsRules.DEFAULT_MISSED_WINDOW_HOURS,
    /** Aggressive mode: reminders are set like an alarm clock, which no battery saver delays. */
    val alarmClock: Boolean = false,
    /**
     * Robust mode: a foreground service keeps the process alive on phones that kill apps in the
     * background. Off by default: it shows a fixed notification.
     */
    val robustMode: Boolean = false,
    /** When all-day events remind, as minutes after midnight. */
    val allDayMinute: Int = SettingsRules.DEFAULT_ALL_DAY_MINUTE
) {
    /** The same settings with every value brought into what [SettingsRules] accepts. */
    fun sanitized(): AppSettings = copy(
        defaultCalendar = defaultCalendar?.takeIf { it.value >= 0 },
        defaultDurationMinutes = SettingsRules.duration(defaultDurationMinutes),
        defaultReminders = SettingsRules.reminders(defaultReminders),
        defaultAllDayReminders = SettingsRules.reminders(defaultAllDayReminders),
        ownEmails = SettingsRules.aliases(ownEmails),
        missedWindowHours = SettingsRules.missedWindow(missedWindowHours),
        allDayMinute = SettingsRules.allDayMinute(allDayMinute)
    )
}
