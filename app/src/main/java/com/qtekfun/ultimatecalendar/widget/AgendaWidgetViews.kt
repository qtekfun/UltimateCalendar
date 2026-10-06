// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.widget.AgendaWidgetRow
import com.qtekfun.ultimatecalendar.domain.widget.WidgetPalette
import com.qtekfun.ultimatecalendar.domain.widget.WidgetTap
import java.time.LocalDate

/** Draws the Agenda widget (T38): its header and the hook of its list; the rows come from the service. */
internal object AgendaWidgetViews {
    // The service-backed list is the only collection API on Android 8 to 11 (minSdk 26).
    @Suppress("DEPRECATION")
    fun frame(
        context: Context,
        appWidgetId: Int,
        today: LocalDate,
        palette: WidgetPalette,
        formats: WidgetFormats
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_agenda)
        views.paintBackground(palette)
        views.setTextViewText(R.id.widget_date, formats.shortDate(today))
        views.setTextColor(R.id.widget_date, palette.text)
        views.setContentDescription(R.id.widget_date, formats.longDate(today))
        views.setOnClickPendingIntent(
            R.id.widget_date,
            WidgetIntents.open(context, WidgetTap.OpenApp)
        )
        views.tint(R.id.widget_add, palette.accent)
        views.setOnClickPendingIntent(
            R.id.widget_add,
            WidgetIntents.open(context, WidgetTap.NewEvent)
        )
        val adapter = Intent(context, AgendaWidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .setData(Uri.fromParts("ucwidget", "agenda/$appWidgetId", null))
        views.setRemoteAdapter(R.id.widget_list, adapter)
        views.setPendingIntentTemplate(
            R.id.widget_list,
            WidgetIntents.template(context, appWidgetId)
        )
        return views
    }

    fun message(context: Context, text: String, palette: WidgetPalette): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_agenda_message).apply {
            setTextViewText(R.id.message_root, text)
            setTextColor(R.id.message_root, palette.secondaryText)
            setOnClickFillInIntent(R.id.message_root, WidgetIntents.fillIn(WidgetTap.OpenApp))
        }

    fun day(
        context: Context,
        row: AgendaWidgetRow.Day,
        palette: WidgetPalette,
        formats: WidgetFormats
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_agenda_day).apply {
        val label = formats.dayLabel(row)
        setTextViewText(R.id.day_root, label)
        setTextColor(R.id.day_root, if (row.isToday) palette.accent else palette.secondaryText)
        setContentDescription(R.id.day_root, label)
        setOnClickFillInIntent(R.id.day_root, WidgetIntents.fillIn(WidgetTap.OpenDay(row.date)))
    }

    fun event(
        context: Context,
        row: AgendaWidgetRow.Event,
        palette: WidgetPalette,
        formats: WidgetFormats
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_agenda_event).apply {
        val entry = row.entry
        val title = entry.instance.title.ifBlank { context.getString(R.string.month_untitled) }
        val time = formats.time(entry.slot)
        val location = entry.instance.location?.takeIf { it.isNotBlank() }
        val color = entry.color ?: palette.accent
        // An invitation not answered yet is an outlined dot, like its chip in the app.
        setViewVisibility(R.id.event_marker, if (entry.isPending) View.GONE else View.VISIBLE)
        setViewVisibility(
            R.id.event_marker_pending,
            if (entry.isPending) View.VISIBLE else View.GONE
        )
        tint(R.id.event_marker, color)
        tint(R.id.event_marker_pending, color)
        setTextViewText(R.id.event_title, title)
        setTextColor(R.id.event_title, palette.text)
        setTextViewText(R.id.event_detail, listOfNotNull(time, location).joinToString(SEPARATOR))
        setTextColor(R.id.event_detail, palette.secondaryText)
        val spoken = listOfNotNull(
            title,
            time,
            location,
            context.getString(R.string.cal_status_pending).takeIf { entry.isPending }
        ).joinToString(", ")
        setContentDescription(R.id.event_root, spoken)
        setOnClickFillInIntent(R.id.event_root, WidgetIntents.fillIn(row.tap))
    }

    private const val SEPARATOR = " · "
}
