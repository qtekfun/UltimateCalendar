// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.firstrun

import android.content.Context
import androidx.core.content.edit
import com.qtekfun.ultimatecalendar.domain.firstrun.FirstRunFlag
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Stand-in until the settings repository exists: the flag in its own preferences file. */
@Singleton
class PreferencesFirstRunFlag @Inject constructor(@ApplicationContext context: Context) :
    FirstRunFlag {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun isDone(): Boolean = preferences.getBoolean(KEY_DONE, false)

    override fun markDone() = preferences.edit { putBoolean(KEY_DONE, true) }

    private companion object {
        const val PREFERENCES = "first_run"
        const val KEY_DONE = "wizard_shown"
    }
}
