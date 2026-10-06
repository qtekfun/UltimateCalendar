// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.detail

import com.qtekfun.ultimatecalendar.domain.recurrence.Frequency
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/**
 * One piece of the sentence that says how an event repeats. The describer decides the pieces;
 * the UI puts each in words (English or Spanish) and joins them. Ordinals are 1 to 5 or -1
 * (the last); a month day of -1 is the last day of the month.
 */
sealed interface RepeatPhrase {
    /** "Every 2 weeks", or "Weekly" when [interval] is 1. */
    data class Every(val frequency: Frequency, val interval: Int) : RepeatPhrase

    /** Monday to Friday, every week. */
    data object EveryWeekday : RepeatPhrase

    data class OnWeekdays(val days: List<DayOfWeek>) : RepeatPhrase

    data class OnMonthDays(val days: List<Int>) : RepeatPhrase

    /** "the third Friday". */
    data class OnOrdinalWeekday(val ordinal: Int, val day: DayOfWeek) : RepeatPhrase

    /** A date every year: "on March 3". */
    data class OnDate(val month: Month, val day: Int) : RepeatPhrase

    data class InMonths(val months: List<Month>) : RepeatPhrase

    /** "the third Friday of March". */
    data class OrdinalWeekdayOfMonth(val ordinal: Int, val day: DayOfWeek, val month: Month) :
        RepeatPhrase

    data class Times(val count: Int) : RepeatPhrase

    /** The repetition ends after this day (inclusive). */
    data class Until(val date: LocalDate) : RepeatPhrase

    /** A rule the app cannot put in words. */
    data object Custom : RepeatPhrase
}
