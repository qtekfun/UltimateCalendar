// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** The dates a rule selects in one period (day, week, month or year) of a series. */
internal object PeriodDates {
    /**
     * The sorted dates of period number [index] of [rule] for a series starting on [start],
     * after BYSETPOS. Dates before [start] may be included; the caller drops them.
     */
    fun of(rule: RecurrenceRule, start: LocalDate, index: Long): List<LocalDate> {
        val step = index * rule.interval
        val dates = when (rule.frequency) {
            Frequency.DAILY -> daily(rule, start.plusDays(step))
            Frequency.WEEKLY -> weekly(rule, start, step)
            Frequency.MONTHLY -> monthly(rule, YearMonth.from(start).plusMonths(step), start)
            Frequency.YEARLY -> yearly(rule, start.year + step.toInt(), start)
        }.distinct().sorted()
        return if (rule.bySetPos.isEmpty()) dates else pickPositions(dates, rule.bySetPos)
    }

    /** The first day of period number [index], to know when the periods are past a range. */
    fun firstDay(rule: RecurrenceRule, start: LocalDate, index: Long): LocalDate {
        val step = index * rule.interval
        return when (rule.frequency) {
            Frequency.DAILY -> start.plusDays(step)
            Frequency.WEEKLY -> weekStart(rule, start, step)
            Frequency.MONTHLY -> YearMonth.from(start).plusMonths(step).atDay(1)
            Frequency.YEARLY -> LocalDate.of(start.year + step.toInt(), 1, 1)
        }
    }

    private fun weekStart(rule: RecurrenceRule, start: LocalDate, step: Long): LocalDate =
        start.with(TemporalAdjusters.previousOrSame(rule.weekStart)).plusWeeks(step)

    private fun daily(rule: RecurrenceRule, day: LocalDate): List<LocalDate> = listOf(day).filter {
        inMonths(rule, it) && inMonthDays(rule, it) &&
            (rule.byDay.isEmpty() || rule.byDay.any { w -> w.day == it.dayOfWeek })
    }

    private fun weekly(rule: RecurrenceRule, start: LocalDate, step: Long): List<LocalDate> {
        val weekStart = weekStart(rule, start, step)
        val days = rule.byDay.map { it.day }.ifEmpty { listOf(start.dayOfWeek) }
        return days.map { weekStart.with(TemporalAdjusters.nextOrSame(it)) }.filter {
            inMonths(rule, it) && inMonthDays(rule, it)
        }
    }

    private fun monthly(rule: RecurrenceRule, month: YearMonth, start: LocalDate): List<LocalDate> =
        if (rule.byMonth.isNotEmpty() && month.monthValue !in rule.byMonth) {
            emptyList()
        } else {
            DayPicks.inMonth(rule, month, start)
        }

    private fun yearly(rule: RecurrenceRule, year: Int, start: LocalDate): List<LocalDate> =
        if (rule.byDay.isNotEmpty() && rule.byMonth.isEmpty()) {
            val first = LocalDate.of(year, 1, 1)
            DayPicks.weekdays(rule.byDay, first, first.plusYears(1).minusDays(1)).filter {
                inMonthDays(rule, it)
            }
        } else {
            val months = rule.byMonth.ifEmpty { listOf(start.monthValue) }
            months.flatMap { DayPicks.inMonth(rule, YearMonth.of(year, it), start) }
        }

    private fun pickPositions(dates: List<LocalDate>, positions: List<Int>): List<LocalDate> =
        positions.mapNotNull { DayPicks.position(dates, it) }.distinct().sorted()

    private fun inMonths(rule: RecurrenceRule, date: LocalDate) =
        rule.byMonth.isEmpty() || date.monthValue in rule.byMonth

    private fun inMonthDays(rule: RecurrenceRule, date: LocalDate) = rule.byMonthDay.isEmpty() ||
        rule.byMonthDay.any { DayPicks.dayOfMonth(YearMonth.from(date), it) == date }
}
