// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.qtekfun.ultimatecalendar.domain.widget.WidgetTap
import com.qtekfun.ultimatecalendar.notify.NotificationRoute
import com.qtekfun.ultimatecalendar.ui.MainActivity

/**
 * The intents behind the taps of the widgets (T38). Taps that open the app carry a [WidgetTap]
 * as text and go to `MainActivity` by its class, so no other app is addressed; they are
 * immutable except the list template, whose rows fill in their own tap.
 */
internal object WidgetIntents {
    private const val ACTION_TAP = "com.qtekfun.ultimatecalendar.action.WIDGET_TAP"
    const val ACTION_SHIFT_MONTH = "com.qtekfun.ultimatecalendar.action.WIDGET_SHIFT_MONTH"
    const val EXTRA_DELTA = "widget_month_delta"
    private const val EXTRA_TAP = "widget_tap"
    private const val SCHEME = "ucwidget"

    /** Opens the app for [tap] (a tap of the Month widget or a button). */
    fun open(context: Context, tap: WidgetTap): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        base(
            context
        ).setData(Uri.fromParts(SCHEME, tap.encode(), null)).putExtra(EXTRA_TAP, tap.encode()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** The template of the Agenda list: each row adds its tap with [fillIn]. */
    fun template(context: Context, appWidgetId: Int): PendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId,
        base(context),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun fillIn(tap: WidgetTap): Intent = Intent().putExtra(EXTRA_TAP, tap.encode())

    /** Moves the Month widget [delta] months; 0 goes back to the current month. */
    fun shiftMonth(context: Context, appWidgetId: Int, delta: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, MonthWidgetProvider::class.java)
                .setAction(ACTION_SHIFT_MONTH)
                .setData(Uri.fromParts(SCHEME, "month/$appWidgetId/$delta", null))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra(EXTRA_DELTA, delta),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Where a widget tap that started the app asks it to go, or null for an ordinary start. */
    fun routeOf(intent: Intent): NotificationRoute? {
        if (intent.action != ACTION_TAP) return null
        return when (val tap = WidgetTap.decode(intent.getStringExtra(EXTRA_TAP))) {
            is WidgetTap.OpenDay -> NotificationRoute.Day(tap.date)
            is WidgetTap.OpenEvent -> NotificationRoute.Event(tap.ref)
            WidgetTap.NewEvent -> NotificationRoute.NewEvent
            WidgetTap.OpenApp, null -> null
        }
    }

    private fun base(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_TAP)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
