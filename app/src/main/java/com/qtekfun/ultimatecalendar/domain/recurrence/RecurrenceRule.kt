// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** A weekday, optionally the n-th of the month or year (`2TU`, `-1FR`). */
data class WeekdayNum(val day: DayOfWeek, val ordinal: Int? = null)

/**
 * The `UNTIL` of a rule. RFC 5545 requires it to be of the same kind as the event's start: a
 * date for all-day events, a UTC moment for timed ones.
 */
sealed interface Until {
    /** The last day (inclusive) an occurrence of an all-day event may fall on. */
    data class Day(val date: LocalDate) : Until

    /** The last instant (inclusive) a timed occurrence may start at. */
    data class Moment(val at: Instant) : Until

    /** The last day of the rule as seen from [zone]. */
    fun lastDay(zone: ZoneId): LocalDate = when (this) {
        is Day -> date
        is Moment -> at.atZone(zone).toLocalDate()
    }
}

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
    val until: Until? = null,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY
)
