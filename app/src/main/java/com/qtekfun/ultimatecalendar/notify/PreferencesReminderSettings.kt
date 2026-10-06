// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stand-in for the settings repository: defaults, and the robust mode switch kept on disk. */
@Singleton
class PreferencesReminderSettings @Inject constructor(@ApplicationContext context: Context) :
    ReminderSettingsSource {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val state =
        MutableStateFlow(ReminderSettings(robustMode = preferences.getBoolean(KEY_ROBUST, false)))

    override val settings: Flow<ReminderSettings> = state.asStateFlow()

    override fun setRobustMode(on: Boolean) {
        preferences.edit { putBoolean(KEY_ROBUST, on) }
        state.value = state.value.copy(robustMode = on)
    }

    private companion object {
        const val PREFERENCES = "reminder_settings"
        const val KEY_ROBUST = "robust_mode"
    }
}
