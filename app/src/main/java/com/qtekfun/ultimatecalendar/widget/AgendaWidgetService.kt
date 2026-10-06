// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.widget.WidgetLoad
import com.qtekfun.ultimatecalendar.domain.widget.AgendaWidgetRow
import com.qtekfun.ultimatecalendar.domain.widget.WidgetPalette
import java.time.ZoneId
import kotlinx.coroutines.runBlocking

/** Feeds the rows of the Agenda widget's list. Only the system binds to it (BIND_REMOTEVIEWS). */
class AgendaWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        AgendaViewsFactory(applicationContext)
}

/** What a row of the list is: an agenda row, or a message that opens the app when tapped. */
private sealed interface ListItem {
    data class Agenda(val row: AgendaWidgetRow) : ListItem

    data class Message(val text: String) : ListItem
}

/**
 * The list of the Agenda widget. The system asks it to reload on [onDataSetChanged], on a
 * binder thread where blocking is allowed, so the data is read there with the repository.
 */
private class AgendaViewsFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {
    private val entry = WidgetEntryPoint.of(context)
    private var items: List<ListItem> = emptyList()
    private var palette: WidgetPalette = WidgetPalette.of(
        dark = false,
        amoled = false,
        tones = null
    )
    private var formats = WidgetFormats(context, ZoneId.systemDefault())

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        palette = WidgetTheme.palette(context, entry.settings().current())
        formats = WidgetFormats(context, ZoneId.systemDefault())
        items = when (val load = runBlocking { entry.loader().agenda() }) {
            is WidgetLoad.Loaded -> load.value.rows.map(ListItem::Agenda)
                .ifEmpty { listOf(ListItem.Message(context.getString(R.string.widget_empty))) }

            WidgetLoad.NoPermission ->
                listOf(ListItem.Message(context.getString(R.string.widget_no_permission)))

            WidgetLoad.Failed -> listOf(ListItem.Message(context.getString(R.string.widget_failed)))
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? =
        when (val item = items.getOrNull(position)) {
            null -> null

            is ListItem.Message -> AgendaWidgetViews.message(context, item.text, palette)

            is ListItem.Agenda -> when (val row = item.row) {
                is AgendaWidgetRow.Day -> AgendaWidgetViews.day(context, row, palette, formats)

                is AgendaWidgetRow.Event -> AgendaWidgetViews.event(context, row, palette, formats)

                is AgendaWidgetRow.More -> AgendaWidgetViews.message(
                    context,
                    context.resources.getQuantityString(
                        R.plurals.widget_more_events,
                        row.count,
                        row.count
                    ),
                    palette
                )
            }
        }

    override fun getLoadingView(): RemoteViews? = null

    // Day headers, events and messages.
    override fun getViewTypeCount(): Int = VIEW_TYPES

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    private companion object {
        const val VIEW_TYPES = 3
    }
}
