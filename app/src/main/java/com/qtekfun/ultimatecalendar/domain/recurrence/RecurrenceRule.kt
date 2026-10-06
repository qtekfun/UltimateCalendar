// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** A weekday, optionally the n-th of the month or year (`2TU`, `-1FR`). */
data class WeekdayNum(val day: DayOfWeek, val ordinal: Int? = null)

/**
 * The part of RRULE (RFC 5545 §3.3.10) the app understands: daily to yearly rules with
 * BYDAY, BYMONTHDAY, BYMONTH, BYSETPOS, COUNT and UNTIL. Anything else (BYHOUR, BYWEEKNO…)
 * makes [RecurrenceRules.parse] return null, and the rule is kept untouched.
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val byDay: List<WeekdayNum> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val bySetPos: List<Int> = emptyList(),
    val count: Int? = null,
    /** The last day an occurrence may fall on. */
    val until: LocalDate? = null,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY
)
