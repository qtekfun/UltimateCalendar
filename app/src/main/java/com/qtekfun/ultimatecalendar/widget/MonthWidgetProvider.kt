// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** The Month widget (T38). Its previous/next buttons come back here as broadcasts. */
class MonthWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetIntents.ACTION_SHIFT_MONTH) {
            super.onReceive(context, intent)
            return
        }
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        WidgetStateStore(context).shift(id, intent.getIntExtra(WidgetIntents.EXTRA_DELTA, 0))
        val refresher = WidgetEntryPoint.of(context).refresher()
        runAsync { refresher.refreshMonth(intArrayOf(id)) }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val refresher = WidgetEntryPoint.of(context).refresher()
        runAsync {
            refresher.refreshMonth(appWidgetIds)
            refresher.scheduleMidnight()
        }
    }

    /** A resized widget has room for more or fewer markers. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val refresher = WidgetEntryPoint.of(context).refresher()
        runAsync { refresher.refreshMonth(intArrayOf(appWidgetId)) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WidgetStateStore(context)
        appWidgetIds.forEach(store::forget)
    }

    override fun onEnabled(context: Context) =
        WidgetEntryPoint.of(context).refresher().scheduleMidnight()

    override fun onDisabled(context: Context) =
        WidgetEntryPoint.of(context).refresher().scheduleMidnight()
}
