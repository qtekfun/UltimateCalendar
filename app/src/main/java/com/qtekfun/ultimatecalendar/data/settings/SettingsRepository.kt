// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.qtekfun.ultimatecalendar.domain.firstrun.FirstRunFlag
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindOption
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.settings.FirstDayOfWeek
import com.qtekfun.ultimatecalendar.domain.settings.InitialView
import com.qtekfun.ultimatecalendar.domain.settings.InviteCheckInterval
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val KEY_THEME = "theme"
private const val KEY_AMOLED = "amoled"
private const val KEY_DYNAMIC_COLOR = "dynamic_color"
private const val KEY_FIRST_DAY = "first_day_of_week"
private const val KEY_INITIAL_VIEW = "initial_view"
private const val KEY_DEFAULT_CALENDAR = "default_calendar"
private const val KEY_DURATION = "default_duration_minutes"
private const val KEY_REMINDERS = "default_reminders"
private const val KEY_ALL_DAY_REMINDERS = "default_all_day_reminders"
private const val KEY_INVITE_CHECK = "invite_check"
private const val KEY_OWN_EMAILS = "own_emails"
private const val KEY_NOTIFY_CHANGES = "notify_changes"
private const val KEY_NOTIFY_CANCELLATIONS = "notify_cancellations"
private const val KEY_RE_REMIND = "re_remind"
private const val KEY_MISSED_WINDOW = "missed_window_hours"
private const val KEY_ALARM_CLOCK = "alarm_clock"
private const val KEY_ROBUST_MODE = "robust_mode"
private const val KEY_ALL_DAY_MINUTE = "all_day_minute"
private const val KEY_FIRST_RUN_DONE = "first_run_done"
private const val LEGACY_KEY_ROBUST = "robust_mode"
private const val LEGACY_KEY_FIRST_RUN = "wizard_shown"
private const val LIST_SEPARATOR = ","

/**
 * Per-device preferences (RF-10). They are not calendar data, so they live in SharedPreferences
 * rather than in Room, and need no extra library. Whatever is written is sanitized first, and
 * whatever is read is too, so a damaged value never reaches the rest of the app. It also keeps
 * whether the first-run wizard was shown, which is not a setting the user can change, so it is
 * not part of [AppSettings] and does not travel in backups.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @Named(SETTINGS_PREFERENCES) private val preferences: SharedPreferences,
    @Named(LEGACY_REMINDER_PREFERENCES) private val legacyReminders: SharedPreferences,
    @Named(LEGACY_FIRST_RUN_PREFERENCES) private val legacyFirstRun: SharedPreferences
) : FirstRunFlag {
    init {
        migrateRobustMode(preferences, legacyReminders)
        migrateFirstRun(preferences, legacyFirstRun)
    }

    /** The current settings, and every change after. */
    val settings: Flow<AppSettings> = callbackFlow {
        trySend(current())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(current())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** The settings now, for the first frame of the app. */
    fun current(): AppSettings = read().sanitized()

    /** Changes the settings with [transform]; the result is sanitized before it is kept. */
    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) = write(transform(current()).sanitized())

    override fun isDone(): Boolean = preferences.getBoolean(KEY_FIRST_RUN_DONE, false)

    override fun markDone() = preferences.edit { putBoolean(KEY_FIRST_RUN_DONE, true) }

    /** Replaces every setting at once, from a backup (RF-11). */
    fun restore(restored: AppSettings) = update { restored }

    private fun write(value: AppSettings) = preferences.edit {
        putString(KEY_THEME, value.theme.name)
        putBoolean(KEY_AMOLED, value.amoled)
        putBoolean(KEY_DYNAMIC_COLOR, value.dynamicColor)
        putString(KEY_FIRST_DAY, value.firstDayOfWeek.name)
        putString(KEY_INITIAL_VIEW, value.initialView.name)
        val calendar = value.defaultCalendar
        if (calendar ==
            null
        ) {
            remove(KEY_DEFAULT_CALENDAR)
        } else {
            putLong(KEY_DEFAULT_CALENDAR, calendar.value)
        }
        putInt(KEY_DURATION, value.defaultDurationMinutes)
        putString(KEY_REMINDERS, value.defaultReminders.joinToString(LIST_SEPARATOR))
        putString(KEY_ALL_DAY_REMINDERS, value.defaultAllDayReminders.joinToString(LIST_SEPARATOR))
        putString(KEY_INVITE_CHECK, value.inviteCheck.name)
        putString(KEY_OWN_EMAILS, value.ownEmails.joinToString(LIST_SEPARATOR))
        putBoolean(KEY_NOTIFY_CHANGES, value.notifyChanges)
        putBoolean(KEY_NOTIFY_CANCELLATIONS, value.notifyCancellations)
        putString(KEY_RE_REMIND, value.reRemind.name)
        putInt(KEY_MISSED_WINDOW, value.missedWindowHours)
        putBoolean(KEY_ALARM_CLOCK, value.alarmClock)
        putBoolean(KEY_ROBUST_MODE, value.robustMode)
        putInt(KEY_ALL_DAY_MINUTE, value.allDayMinute)
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enumOf(KEY_THEME, d.theme),
            amoled = preferences.getBoolean(KEY_AMOLED, d.amoled),
            dynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, d.dynamicColor),
            firstDayOfWeek = enumOf<FirstDayOfWeek>(KEY_FIRST_DAY, d.firstDayOfWeek),
            initialView = enumOf<InitialView>(KEY_INITIAL_VIEW, d.initialView),
            defaultCalendar = if (preferences.contains(KEY_DEFAULT_CALENDAR)) {
                CalendarId(preferences.getLong(KEY_DEFAULT_CALENDAR, 0))
            } else {
                null
            },
            defaultDurationMinutes = preferences.getInt(KEY_DURATION, d.defaultDurationMinutes),
            defaultReminders = numbers(KEY_REMINDERS, d.defaultReminders),
            defaultAllDayReminders = numbers(KEY_ALL_DAY_REMINDERS, d.defaultAllDayReminders),
            inviteCheck = enumOf<InviteCheckInterval>(KEY_INVITE_CHECK, d.inviteCheck),
            ownEmails = preferences.getString(KEY_OWN_EMAILS, null)
                ?.split(LIST_SEPARATOR).orEmpty().filter { it.isNotEmpty() },
            notifyChanges = preferences.getBoolean(KEY_NOTIFY_CHANGES, d.notifyChanges),
            notifyCancellations = preferences.getBoolean(
                KEY_NOTIFY_CANCELLATIONS,
                d.notifyCancellations
            ),
            reRemind = enumOf<ReRemindOption>(KEY_RE_REMIND, d.reRemind),
            missedWindowHours = preferences.getInt(KEY_MISSED_WINDOW, d.missedWindowHours),
            alarmClock = preferences.getBoolean(KEY_ALARM_CLOCK, d.alarmClock),
            robustMode = preferences.getBoolean(KEY_ROBUST_MODE, d.robustMode),
            allDayMinute = preferences.getInt(KEY_ALL_DAY_MINUTE, d.allDayMinute)
        )
    }

    private inline fun <reified E : Enum<E>> enumOf(key: String, default: E): E {
        val name = preferences.getString(key, null)
        return enumValues<E>().firstOrNull { it.name == name } ?: default
    }

    /** A list of numbers; an absent key is [default], an empty one is really empty. */
    private fun numbers(key: String, default: List<Int>): List<Int> {
        val text = preferences.getString(key, null) ?: return default
        return text.split(LIST_SEPARATOR).mapNotNull { it.toIntOrNull() }
    }

    companion object {
        const val SETTINGS_PREFERENCES = "settings"
        const val LEGACY_REMINDER_PREFERENCES = "reminder_settings"
        const val LEGACY_FIRST_RUN_PREFERENCES = "first_run"
    }
}

/**
 * Robust mode used to live in its own preferences file (T02b, before this repository). Moves
 * a value found there, unless the settings already have one, so nobody loses it.
 */
private fun migrateRobustMode(preferences: SharedPreferences, legacy: SharedPreferences) {
    if (!legacy.contains(LEGACY_KEY_ROBUST)) return
    val robust = legacy.getBoolean(LEGACY_KEY_ROBUST, false)
    if (!preferences.contains(KEY_ROBUST_MODE)) {
        preferences.edit { putBoolean(KEY_ROBUST_MODE, robust) }
    }
    legacy.edit { remove(LEGACY_KEY_ROBUST) }
}

/**
 * The first-run flag used to live in its own preferences file (T12, before this repository).
 * Moves it once, so whoever already saw the wizard does not see it again.
 */
private fun migrateFirstRun(preferences: SharedPreferences, legacy: SharedPreferences) {
    if (!legacy.contains(LEGACY_KEY_FIRST_RUN)) return
    if (legacy.getBoolean(LEGACY_KEY_FIRST_RUN, false)) {
        preferences.edit { putBoolean(KEY_FIRST_RUN_DONE, true) }
    }
    legacy.edit { remove(LEGACY_KEY_FIRST_RUN) }
}
