// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields

/** Week numbers for the optional week-number display. */
object WeekNumbers {
    /** Weeks need at least this many days in the new year to count as its first (as ISO 8601). */
    private const val MIN_DAYS_IN_FIRST_WEEK = 4

    /**
     * The week of the year of [date] when weeks start on [firstDayOfWeek]. With Monday this is the
     * ISO 8601 number; with Sunday or Saturday, the nearest equivalent that keeps 4 days of the
     * new year as its first week.
     */
    fun of(date: LocalDate, firstDayOfWeek: DayOfWeek): Int =
        date.get(WeekFields.of(firstDayOfWeek, MIN_DAYS_IN_FIRST_WEEK).weekOfWeekBasedYear())
}
