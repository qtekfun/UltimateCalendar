// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** The Agenda widget (T38). The app redraws it; the system never refreshes it on a timer. */
class AgendaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val refresher = WidgetEntryPoint.of(context).refresher()
        runAsync {
            refresher.refreshAgenda(appWidgetIds)
            refresher.scheduleMidnight()
        }
    }

    override fun onEnabled(context: Context) =
        WidgetEntryPoint.of(context).refresher().scheduleMidnight()

    override fun onDisabled(context: Context) =
        WidgetEntryPoint.of(context).refresher().scheduleMidnight()
}
