// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.widget.MonthWidgetCell
import com.qtekfun.ultimatecalendar.domain.widget.MonthWidgetModel
import com.qtekfun.ultimatecalendar.domain.widget.WidgetPalette
import com.qtekfun.ultimatecalendar.domain.widget.WidgetTap

/** Draws the Month widget (T38): the model of the grid into a RemoteViews. */
internal object MonthWidgetViews {
    private const val FILLED = "●"
    private const val OUTLINED = "○"
    private const val NEXT = 1
    private const val PREVIOUS = -1

    fun grid(
        context: Context,
        appWidgetId: Int,
        model: MonthWidgetModel,
        palette: WidgetPalette,
        formats: WidgetFormats
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_month)
        views.paintBackground(palette)
        val title = formats.month(model.month)
        views.setTextViewText(R.id.month_title, title)
        views.setTextColor(R.id.month_title, palette.text)
        views.setContentDescription(
            R.id.month_title,
            context.getString(R.string.widget_this_month, title)
        )
        views.setOnClickPendingIntent(
            R.id.month_title,
            WidgetIntents.shiftMonth(context, appWidgetId, 0)
        )
        views.tint(R.id.month_prev, palette.text)
        views.tint(R.id.month_next, palette.text)
        views.setOnClickPendingIntent(
            R.id.month_prev,
            WidgetIntents.shiftMonth(context, appWidgetId, PREVIOUS)
        )
        views.setOnClickPendingIntent(
            R.id.month_next,
            WidgetIntents.shiftMonth(context, appWidgetId, NEXT)
        )
        MonthWidgetIds.weekdays.zip(model.weekdays).forEach { (id, day) ->
            views.setTextViewText(id, formats.weekday(day))
            views.setTextColor(id, palette.secondaryText)
        }
        MonthWidgetIds.weekRows.forEachIndexed { row, id ->
            views.setViewVisibility(id, if (row < model.weeks.size) View.VISIBLE else View.GONE)
        }
        val style = Style(context, palette, formats)
        model.weeks.forEachIndexed { row, week ->
            week.forEachIndexed { column, cell ->
                cell(
                    views,
                    MonthWidgetIds.cells[row][column],
                    cell,
                    Style(context, palette, formats)
                )
            }
        }
        return views
    }

    /** A whole widget that says why there is nothing to draw; tapping it opens the app. */
    fun message(context: Context, text: String, palette: WidgetPalette): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_message).apply {
            paintBackground(palette)
            setTextViewText(R.id.message_text, text)
            setTextColor(R.id.message_text, palette.text)
            setOnClickPendingIntent(
                R.id.widget_root,
                WidgetIntents.open(context, WidgetTap.OpenApp)
            )
        }

    /** What drawing a cell needs besides the cell. */
    private class Style(
        val context: Context,
        val palette: WidgetPalette,
        val formats: WidgetFormats
    )

    private fun cell(views: RemoteViews, ids: MonthCellIds, cell: MonthWidgetCell, style: Style) {
        val palette = style.palette
        val context = style.context
        val formats = style.formats
        views.setTextViewText(ids.day, cell.date.dayOfMonth.toString())
        views.setTextColor(
            ids.day,
            when {
                cell.isToday -> palette.onAccent
                cell.inMonth -> palette.text
                else -> palette.secondaryText
            }
        )
        views.setViewVisibility(ids.today, if (cell.isToday) View.VISIBLE else View.GONE)
        if (cell.isToday) views.tint(ids.today, palette.accent)
        ids.markers.forEachIndexed { index, id ->
            val marker = cell.markers.getOrNull(index)
            views.setViewVisibility(id, if (marker == null) View.GONE else View.VISIBLE)
            if (marker != null) {
                views.setTextViewText(id, if (marker.pending) OUTLINED else FILLED)
                views.setTextColor(id, marker.color ?: palette.accent)
            }
        }
        views.setViewVisibility(ids.more, if (cell.overflow > 0) View.VISIBLE else View.GONE)
        if (cell.overflow > 0) {
            views.setTextViewText(ids.more, context.getString(R.string.month_more, cell.overflow))
            views.setTextColor(ids.more, palette.secondaryText)
        }
        views.setContentDescription(ids.root, formats.cellDescription(cell))
        views.setOnClickPendingIntent(ids.root, WidgetIntents.open(context, cell.tap))
    }
}
