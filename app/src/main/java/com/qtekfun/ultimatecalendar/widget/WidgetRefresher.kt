// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.data.widget.WidgetDataLoader
import com.qtekfun.ultimatecalendar.data.widget.WidgetLoad
import com.qtekfun.ultimatecalendar.domain.navigation.ViewPeriods
import com.qtekfun.ultimatecalendar.domain.widget.MonthWidgets
import com.qtekfun.ultimatecalendar.domain.widget.WidgetRefreshTimes
import com.qtekfun.ultimatecalendar.notify.SystemZone
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the home-screen widgets up to date (T38) without polling. While the process lives it
 * redraws them when the calendar provider, a calendar's local settings or the app settings
 * change (the provider's observer reaches it through the repository); an alarm at midnight and
 * the time and zone broadcasts move "today" on; and the app opening redraws them too.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CalendarRepository,
    private val loader: WidgetDataLoader,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val zone: SystemZone
) {
    private val manager: AppWidgetManager get() = AppWidgetManager.getInstance(context)
    private val state = WidgetStateStore(context)

    /** Redraws on every change of the calendars until [scope] ends. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            // Emits at once and again on every provider or local calendar change.
            repository.calendars().collectLatest {
                // A burst of changes (a sync) draws once.
                delay(DEBOUNCE_MS)
                refreshAll()
            }
        }
        scope.launch {
            settings.settings.collectLatest { refreshAll() }
        }
    }

    suspend fun refreshAll() {
        refreshAgenda(idsOf(AgendaWidgetProvider::class.java))
        refreshMonth(idsOf(MonthWidgetProvider::class.java))
    }

    // Reloads the service-backed list, as on every Android version since 3.
    @Suppress("DEPRECATION")
    suspend fun refreshAgenda(ids: IntArray) {
        if (ids.isEmpty()) return
        val palette = WidgetTheme.palette(context, settings.current())
        val zoneNow = zone.current()
        val formats = WidgetFormats(context, zoneNow)
        val today = ViewPeriods.today(clock, zoneNow)
        ids.forEach { id ->
            manager.updateAppWidget(
                id,
                AgendaWidgetViews.frame(context, id, today, palette, formats)
            )
        }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }

    suspend fun refreshMonth(ids: IntArray) {
        if (ids.isEmpty()) return
        val palette = WidgetTheme.palette(context, settings.current())
        val formats = WidgetFormats(context, zone.current())
        ids.forEach { id ->
            val markers = MonthWidgets.markerCount(cellHeightDp(manager.getAppWidgetOptions(id)))
            val views = when (val load = loader.month(state.offset(id), markers)) {
                is WidgetLoad.Loaded -> MonthWidgetViews.grid(
                    context,
                    id,
                    load.value,
                    palette,
                    formats
                )

                WidgetLoad.NoPermission ->
                    MonthWidgetViews.message(
                        context,
                        context.getString(R.string.widget_no_permission),
                        palette
                    )

                WidgetLoad.Failed ->
                    MonthWidgetViews.message(
                        context,
                        context.getString(R.string.widget_failed),
                        palette
                    )
            }
            manager.updateAppWidget(id, views)
        }
    }

    /** Sets the one alarm of the next midnight; none when no widget is on the home screen. */
    fun scheduleMidnight() {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = midnightIntent()
        if (idsOf(AgendaWidgetProvider::class.java).isEmpty() &&
            idsOf(MonthWidgetProvider::class.java).isEmpty()
        ) {
            alarms.cancel(pending)
            return
        }
        val at = WidgetRefreshTimes.nextMidnight(clock.instant(), zone.current())
        // Inexact on purpose: a widget a few minutes late at midnight is not worth an exact alarm.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, at.toEpochMilli(), pending)
    }

    private fun midnightIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(
            context,
            WidgetRefreshReceiver::class.java
        ).setAction(WidgetRefreshReceiver.ACTION_MIDNIGHT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun idsOf(provider: Class<*>): IntArray =
        manager.getAppWidgetIds(ComponentName(context, provider))

    private fun cellHeightDp(options: Bundle): Int {
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            .takeIf { it > 0 } ?: DEFAULT_HEIGHT_DP
        return (height - CHROME_DP) / ASSUMED_ROWS
    }

    private companion object {
        const val DEBOUNCE_MS = 1_000L
        const val DEFAULT_HEIGHT_DP = 250

        /** The title bar, the weekday row and the padding, and the rows a month usually has. */
        const val CHROME_DP = 70
        const val ASSUMED_ROWS = 5
    }
}
