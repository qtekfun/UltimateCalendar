// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Midnight, a new date, a new time or a new zone: "today" is somewhere else, so the widgets are
 * drawn again and the next midnight is planned. Not exported: it only hears the system and the
 * app's own alarm.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val refresher = WidgetEntryPoint.of(context).refresher()
        runAsync {
            refresher.refreshAll()
            refresher.scheduleMidnight()
        }
    }

    companion object {
        const val ACTION_MIDNIGHT = "com.qtekfun.ultimatecalendar.action.WIDGET_MIDNIGHT"

        /** The manifest declares the three system ones. */
        private val ACTIONS = setOf(
            ACTION_MIDNIGHT,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
