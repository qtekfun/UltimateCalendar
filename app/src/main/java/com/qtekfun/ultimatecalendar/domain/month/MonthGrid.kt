// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.month

import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * The days a Month page draws (RF-03): whole weeks from the one holding the 1st to the one
 * holding the last day, so 4 to 6 rows (never padded to 6). [weeks] are lists of 7 days that
 * start on the first day of the week; days of the neighbouring months are part of the grid.
 */
data class MonthGrid(val month: YearMonth, val weeks: List<List<LocalDate>>) {
    /** Every day of the grid, in order. */
    val days: List<LocalDate> get() = weeks.flatten()

    /** The days to ask the source for: the whole visible grid, in one read. */
    val range: DateRange get() = DateRange(weeks.first().first(), weeks.last().last().plusDays(1))

    /** Whether [date] belongs to the month (not to a neighbour shown in the grid). */
    fun isInMonth(date: LocalDate): Boolean = YearMonth.from(date) == month

    companion object {
        const val DAYS_IN_WEEK = 7

        fun of(month: YearMonth, firstDayOfWeek: DayOfWeek): MonthGrid {
            val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            val lastDayOfWeek = firstDayOfWeek.minus(1)
            val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(lastDayOfWeek))
            val weeks = generateSequence(start) { it.plusDays(DAYS_IN_WEEK.toLong()) }
                .takeWhile { !it.isAfter(end) }
                .map { first -> List(DAYS_IN_WEEK) { first.plusDays(it.toLong()) } }
                .toList()
            return MonthGrid(month, weeks)
        }
    }
}
