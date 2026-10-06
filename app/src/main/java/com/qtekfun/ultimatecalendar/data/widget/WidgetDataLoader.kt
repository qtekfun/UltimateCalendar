// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.widget

import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaDays
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import com.qtekfun.ultimatecalendar.domain.navigation.FirstDayOfWeekSource
import com.qtekfun.ultimatecalendar.domain.navigation.ViewPeriods
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.widget.AgendaWidgetRow
import com.qtekfun.ultimatecalendar.domain.widget.AgendaWidgetRows
import com.qtekfun.ultimatecalendar.domain.widget.MonthWidgetModel
import com.qtekfun.ultimatecalendar.domain.widget.MonthWidgets
import com.qtekfun.ultimatecalendar.notify.SystemZone
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** What a widget can show: its content, or why there is none. */
sealed interface WidgetLoad<out T> {
    data class Loaded<T>(val value: T) : WidgetLoad<T>

    /** The calendar permission is not granted: the widget says so and opens the app. */
    data object NoPermission : WidgetLoad<Nothing>

    data object Failed : WidgetLoad<Nothing>
}

/** The Agenda widget's rows and the day it was read for. */
data class AgendaWidgetContent(val today: LocalDate, val rows: List<AgendaWidgetRow>)

/**
 * Reads what the home-screen widgets draw (T38) through the [CalendarRepository], so hidden
 * calendars and the user's visibility overrides apply, and the day is the device's, now.
 */
@Singleton
class WidgetDataLoader @Inject constructor(
    private val repository: CalendarRepository,
    private val clock: Clock,
    private val zone: SystemZone,
    private val firstDay: FirstDayOfWeekSource
) {
    suspend fun agenda(): WidgetLoad<AgendaWidgetContent> {
        val zoneNow = zone.current()
        val today = ViewPeriods.today(clock, zoneNow)
        val range = AgendaWidgetRows.range(today)
        return read(range, zoneNow) { instances, colors ->
            val days = AgendaDays.build(range, zoneNow, instances, colors)
            AgendaWidgetContent(today, AgendaWidgetRows.build(days, today, clock.instant()))
        }
    }

    /** The month [offset] months from the current one, with [markers] dots a day at most. */
    suspend fun month(offset: Int, markers: Int): WidgetLoad<MonthWidgetModel> {
        val zoneNow = zone.current()
        val today = ViewPeriods.today(clock, zoneNow)
        val month = MonthWidgets.shown(today, offset)
        val first = firstDay.current()
        return read(MonthWidgets.range(month, first), zoneNow) { instances, colors ->
            MonthWidgets.build(month, today, first, zoneNow, instances, colors, markers)
        }
    }

    private suspend fun <T> read(
        range: DateRange,
        zoneNow: ZoneId,
        build: (List<EventInstance>, Map<CalendarId, Int>) -> T
    ): WidgetLoad<T> {
        val calendars = repository.visibleCalendars().first()
        val instances = repository.instances(range.toTimeRange(zoneNow)).first()
        return when {
            calendars is CalendarResult.Failure -> failure(calendars.error)

            instances is CalendarResult.Failure -> failure(instances.error)

            else -> WidgetLoad.Loaded(
                build(
                    instances.getOrNull().orEmpty(),
                    calendars.getOrNull().orEmpty().associate { it.id to it.color }
                )
            )
        }
    }

    private fun failure(error: CalendarError): WidgetLoad<Nothing> =
        if (error == CalendarError.PermissionDenied) WidgetLoad.NoPermission else WidgetLoad.Failed
}
