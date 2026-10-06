// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.widget

import android.content.Context
import androidx.core.content.edit
import com.qtekfun.ultimatecalendar.domain.widget.MonthWidgets

/** Which month each Month widget shows, as months from the current one (0 is this month). */
internal class WidgetStateStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun offset(appWidgetId: Int): Int = preferences.getInt(key(appWidgetId), 0)

    /** Moves by [delta] months; 0 returns to the current one. */
    fun shift(appWidgetId: Int, delta: Int): Int {
        val next = if (delta == 0) 0 else MonthWidgets.clampOffset(offset(appWidgetId) + delta)
        preferences.edit { putInt(key(appWidgetId), next) }
        return next
    }

    fun forget(appWidgetId: Int) = preferences.edit { remove(key(appWidgetId)) }

    private fun key(appWidgetId: Int) = "month_offset_$appWidgetId"

    private companion object {
        const val FILE = "widget_state"
    }
}
