// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.recurrence.CustomRepeat
import com.qtekfun.ultimatecalendar.domain.recurrence.Frequency
import com.qtekfun.ultimatecalendar.domain.recurrence.MonthlyMode
import com.qtekfun.ultimatecalendar.domain.recurrence.RepeatEnd
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/** Where in the month a monthly repetition falls. */
sealed interface MonthlyDay {
    /** The same day number every month ("on day 15"). */
    data class OfMonth(val day: Int) : MonthlyDay

    /** The n-th weekday ("the 3rd Friday"); [ordinal] is -1 for the last. */
    data class Nth(val ordinal: Int, val weekday: DayOfWeek) : MonthlyDay
}

/**
 * What a custom repetition says, in the pieces a sentence is made of, with everything the rule
 * leaves implicit (the weekday, the day of the month, the month of the year) filled in from the
 * event's date. The screen turns it into words in the user's language, so no text lives here.
 */
data class RepeatSummary(
    val frequency: Frequency,
    val interval: Int,
    /** Weekly: the days, sorted from Monday. */
    val weekdays: List<DayOfWeek>,
    /** Monthly: the day. */
    val monthly: MonthlyDay?,
    /** Yearly: the month and day of the event. */
    val yearlyMonth: Month?,
    val yearlyDay: Int?,
    val end: RepeatEnd,
    val until: LocalDate?,
    val count: Int
) {
    companion object {
        private const val DAYS_IN_WEEK = 7
        private const val MAX_NTH = 4

        /** The summary of [repeat] for an event that starts on [anchor]. */
        fun of(repeat: CustomRepeat, anchor: LocalDate): RepeatSummary {
            val frequency = repeat.frequency
            val yearly = frequency == Frequency.YEARLY
            return RepeatSummary(
                frequency = frequency,
                interval = repeat.interval.coerceAtLeast(1),
                weekdays = if (frequency == Frequency.WEEKLY) {
                    repeat.weekdays.ifEmpty { setOf(anchor.dayOfWeek) }.sorted()
                } else {
                    emptyList()
                },
                monthly = if (frequency == Frequency.MONTHLY) monthlyOf(repeat, anchor) else null,
                yearlyMonth = anchor.month.takeIf { yearly },
                yearlyDay = anchor.dayOfMonth.takeIf { yearly },
                end = repeat.end,
                until = repeat.until.takeIf { repeat.end == RepeatEnd.ON_DATE },
                count = repeat.count.coerceAtLeast(1)
            )
        }

        private fun monthlyOf(repeat: CustomRepeat, anchor: LocalDate): MonthlyDay =
            if (repeat.monthlyMode == MonthlyMode.WEEKDAY_OF_MONTH) {
                MonthlyDay.Nth(repeat.ordinal, repeat.weekday)
            } else {
                MonthlyDay.OfMonth(anchor.dayOfMonth)
            }

        /**
         * The ways a monthly rule can pick the day of [date]: its number, its n-th weekday (up
         * to the 4th) and, when it is the last of that weekday, "the last Friday".
         */
        fun monthlyOptions(date: LocalDate): List<MonthlyDay> = buildList {
            add(MonthlyDay.OfMonth(date.dayOfMonth))
            val nth = (date.dayOfMonth - 1) / DAYS_IN_WEEK + 1
            if (nth <= MAX_NTH) add(MonthlyDay.Nth(nth, date.dayOfWeek))
            if (date.plusDays(DAYS_IN_WEEK.toLong()).month != date.month) {
                add(MonthlyDay.Nth(-1, date.dayOfWeek))
            }
        }
    }
}
