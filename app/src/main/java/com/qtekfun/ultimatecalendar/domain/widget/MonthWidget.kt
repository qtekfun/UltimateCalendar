// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.widget

import com.qtekfun.ultimatecalendar.domain.agenda.AgendaDays
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.month.MonthGrid
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A dot under a day number: the event's color, outlined while its invitation is unanswered. */
data class MonthWidgetMarker(val color: Int?, val pending: Boolean)

/** One day of the Month widget. Tapping it opens the Day view on [date]. */
data class MonthWidgetCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val markers: List<MonthWidgetMarker>,
    /** How many events have no dot of their own: drawn as "+N". */
    val overflow: Int,
    val eventCount: Int,
    val hasPending: Boolean
) {
    val tap: WidgetTap get() = WidgetTap.OpenDay(date)
}

/** The month the widget draws: [weeks] of 7 cells from the first day of the week. */
data class MonthWidgetModel(
    val month: YearMonth,
    val weekdays: List<DayOfWeek>,
    val weeks: List<List<MonthWidgetCell>>
)

/**
 * The Month widget (T38): its grid with a few markers a day, and which month it shows. The
 * grid is the app's [MonthGrid] and the days are bucketed like the agenda's, so a multi-day
 * event marks each day it touches and all-day events come first.
 */
object MonthWidgets {
    const val MAX_OFFSET = 120

    /** Cell heights (dp) from which a cell has room for 2 and for 3 markers. */
    private const val TWO_MARKERS_DP = 40
    private const val THREE_MARKERS_DP = 52
    private const val MAX_MARKERS = 3

    /** The days to read for [month]: its whole grid. */
    fun range(month: YearMonth, firstDayOfWeek: DayOfWeek): DateRange =
        MonthGrid.of(month, firstDayOfWeek).range

    /** The month shown [offset] months from today's (clamped to ten years either way). */
    fun shown(today: LocalDate, offset: Int): YearMonth =
        YearMonth.from(today).plusMonths(clampOffset(offset).toLong())

    fun clampOffset(offset: Int): Int = offset.coerceIn(-MAX_OFFSET, MAX_OFFSET)

    /** How many dots fit in a cell [cellHeightDp] tall; one at least, three at most. */
    fun markerCount(cellHeightDp: Int): Int = when {
        cellHeightDp >= THREE_MARKERS_DP -> MAX_MARKERS
        cellHeightDp >= TWO_MARKERS_DP -> 2
        else -> 1
    }

    @Suppress("LongParameterList")
    fun build(
        month: YearMonth,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
        zone: ZoneId,
        instances: List<EventInstance>,
        calendarColors: Map<CalendarId, Int>,
        maxMarkers: Int
    ): MonthWidgetModel {
        require(maxMarkers > 0) { "A cell shows at least one marker" }
        val grid = MonthGrid.of(month, firstDayOfWeek)
        val byDay = AgendaDays.build(grid.range, zone, instances, calendarColors)
            .associate { it.date to it.entries }
        val weeks = grid.weeks.map { week ->
            week.map { date ->
                val entries = byDay[date].orEmpty()
                val dots = minOf(entries.size, maxMarkers)
                MonthWidgetCell(
                    date = date,
                    inMonth = grid.isInMonth(date),
                    isToday = date == today,
                    markers = entries.take(dots).map { MonthWidgetMarker(it.color, it.isPending) },
                    overflow = entries.size - dots,
                    eventCount = entries.size,
                    hasPending = entries.any { it.isPending }
                )
            }
        }
        val weekdays = List(MonthGrid.DAYS_IN_WEEK) { firstDayOfWeek.plus(it.toLong()) }
        return MonthWidgetModel(month, weekdays, weeks)
    }
}
