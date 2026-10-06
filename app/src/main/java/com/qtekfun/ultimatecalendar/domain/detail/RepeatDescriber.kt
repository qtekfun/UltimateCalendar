// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.detail

import com.qtekfun.ultimatecalendar.domain.recurrence.Frequency
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceRule
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceRules
import java.time.DayOfWeek
import java.time.Month
import java.time.ZoneId

/**
 * Says in words how an event repeats (RF-04), as a list of [RepeatPhrase]s: the pure decision of
 * what to say; the strings, with their plurals, are in the UI. Rules the app cannot read give
 * [RepeatPhrase.Custom]; an event that does not repeat gives nothing.
 */
object RepeatDescriber {
    private val WEEKDAYS = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    )

    /** [zone] decides which day a moment-like `UNTIL` falls on. */
    fun describe(rrule: String?, zone: ZoneId): List<RepeatPhrase> = when (rrule) {
        null -> emptyList()

        else -> RecurrenceRules.parse(rrule)?.let { phrases(it, zone) }
            ?: listOf(RepeatPhrase.Custom)
    }

    private fun phrases(rule: RecurrenceRule, zone: ZoneId): List<RepeatPhrase> = buildList {
        if (isEveryWeekday(rule)) {
            add(RepeatPhrase.EveryWeekday)
        } else {
            add(RepeatPhrase.Every(rule.frequency, rule.interval))
            addAll(qualifiers(rule))
        }
        rule.count?.let { add(RepeatPhrase.Times(it)) }
        rule.until?.let { add(RepeatPhrase.Until(it.lastDay(zone))) }
    }

    private fun isEveryWeekday(rule: RecurrenceRule) = rule.frequency == Frequency.WEEKLY &&
        rule.interval == 1 && rule.byDay.none { it.ordinal != null } &&
        rule.byDay.map { it.day }.toSet() == WEEKDAYS

    private fun qualifiers(rule: RecurrenceRule): List<RepeatPhrase> = when (rule.frequency) {
        Frequency.DAILY, Frequency.WEEKLY -> weekdays(rule)
        Frequency.MONTHLY -> monthly(rule)
        Frequency.YEARLY -> yearly(rule)
    }

    private fun weekdays(rule: RecurrenceRule): List<RepeatPhrase> = if (rule.byDay.isEmpty()) {
        emptyList()
    } else {
        listOf(RepeatPhrase.OnWeekdays(rule.byDay.map { it.day }.distinct().sorted()))
    }

    private fun monthly(rule: RecurrenceRule): List<RepeatPhrase> =
        if (rule.byMonthDay.isNotEmpty()) {
            listOf(RepeatPhrase.OnMonthDays(rule.byMonthDay))
        } else {
            ordinalDays(rule).map { RepeatPhrase.OnOrdinalWeekday(it.first, it.second) }
                .ifEmpty { weekdays(rule) }
        }

    private fun yearly(rule: RecurrenceRule): List<RepeatPhrase> {
        val months = rule.byMonth.map { Month.of(it) }
        val month = months.singleOrNull()
        val day = rule.byMonthDay.singleOrNull()
        val ordinal = ordinalDays(rule).firstOrNull()
        return when {
            month != null && day != null -> listOf(RepeatPhrase.OnDate(month, day))

            month != null && ordinal != null ->
                listOf(RepeatPhrase.OrdinalWeekdayOfMonth(ordinal.first, ordinal.second, month))

            months.isNotEmpty() -> listOf(RepeatPhrase.InMonths(months))

            else -> emptyList()
        }
    }

    /** The "third Friday" days of a rule: from `2FR` weekdays or from `BYSETPOS` with `BYDAY`. */
    private fun ordinalDays(rule: RecurrenceRule): List<Pair<Int, DayOfWeek>> {
        val numbered = rule.byDay.mapNotNull { day -> day.ordinal?.let { it to day.day } }
        val position = rule.bySetPos.singleOrNull()
        return when {
            numbered.isNotEmpty() -> numbered
            position != null && rule.byDay.size == 1 -> listOf(position to rule.byDay.first().day)
            else -> emptyList()
        }
    }
}
