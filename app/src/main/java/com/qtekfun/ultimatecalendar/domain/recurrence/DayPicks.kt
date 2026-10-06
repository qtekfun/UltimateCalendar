// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Picks days inside a month or a year for BYMONTHDAY, BYDAY and BYSETPOS. */
internal object DayPicks {
    /** The days of [month] chosen by BYMONTHDAY and BYDAY, or the series' day when neither. */
    fun inMonth(rule: RecurrenceRule, month: YearMonth, start: LocalDate): List<LocalDate> {
        val byMonthDay = rule.byMonthDay.mapNotNull { dayOfMonth(month, it) }
        val byDay = weekdays(rule.byDay, month.atDay(1), month.atEndOfMonth())
        return when {
            rule.byMonthDay.isNotEmpty() && rule.byDay.isNotEmpty() -> byMonthDay.intersect(
                byDay.toSet()
            ).toList()

            rule.byMonthDay.isNotEmpty() -> byMonthDay

            rule.byDay.isNotEmpty() -> byDay

            else -> listOfNotNull(dayOfMonth(month, start.dayOfMonth))
        }
    }

    /** Day [n] of [month], counted from the end when negative; null when the month lacks it. */
    fun dayOfMonth(month: YearMonth, n: Int): LocalDate? {
        val day = if (n > 0) n else month.lengthOfMonth() + n + 1
        return if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }

    fun weekdays(entries: List<WeekdayNum>, from: LocalDate, to: LocalDate): List<LocalDate> =
        entries.flatMap { entry ->
            val all = generateSequence(from.with(TemporalAdjusters.nextOrSame(entry.day))) {
                it.plusWeeks(1)
            }.takeWhile { !it.isAfter(to) }.toList()
            val ordinal = entry.ordinal
            if (ordinal == null) all else listOfNotNull(position(all, ordinal))
        }

    /** The 1-based [n]-th element, counted from the end when negative; null when out of range. */
    fun <T> position(items: List<T>, n: Int): T? =
        if (n > 0) items.getOrNull(n - 1) else items.getOrNull(items.size + n)
}
