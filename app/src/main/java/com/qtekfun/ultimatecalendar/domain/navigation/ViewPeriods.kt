// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Which dates a view shows and how to move between them. Pure: the date and zone come in. */
object ViewPeriods {
    private const val THREE_DAYS = 3L
    private const val DAYS_IN_WEEK = 7L

    /** Today in [zone], read from the injected [clock]. */
    fun today(clock: Clock, zone: ZoneId): LocalDate = LocalDate.now(clock.withZone(zone))

    /**
     * The days shown when the view is anchored on [date]. A week starts on [firstDayOfWeek]; a
     * month is the calendar month (the grid's leading and trailing days are the view's business);
     * the agenda lists a month onward from [date].
     */
    fun range(view: CalendarView, date: LocalDate, firstDayOfWeek: DayOfWeek): DateRange =
        when (view) {
            CalendarView.AGENDA -> DateRange(date, date.plusMonths(1))

            CalendarView.DAY -> DateRange(date, date.plusDays(1))

            CalendarView.THREE_DAYS -> DateRange(date, date.plusDays(THREE_DAYS))

            CalendarView.WEEK -> {
                val start = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
                DateRange(start, start.plusDays(DAYS_IN_WEEK))
            }

            CalendarView.MONTH -> {
                val start = date.withDayOfMonth(1)
                DateRange(start, start.plusMonths(1))
            }
        }

    /**
     * The anchor date after moving [periods] periods (negative goes back): a day, three days, a
     * week or a month. Months clamp to the shorter month (31 Jan + 1 month = 28 or 29 Feb).
     */
    fun shift(view: CalendarView, date: LocalDate, periods: Int): LocalDate = when (view) {
        CalendarView.DAY -> date.plusDays(periods.toLong())
        CalendarView.THREE_DAYS -> date.plusDays(periods * THREE_DAYS)
        CalendarView.WEEK -> date.plusDays(periods * DAYS_IN_WEEK)
        CalendarView.AGENDA, CalendarView.MONTH -> date.plusMonths(periods.toLong())
    }
}
