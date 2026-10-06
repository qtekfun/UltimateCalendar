// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.detail.RepeatPhrase
import com.qtekfun.ultimatecalendar.domain.recurrence.Frequency
import java.time.DayOfWeek
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** How an event repeats, in words: the head ("Weekly on Monday") then what limits it. */
internal class RepeatWords(
    private val words: WordSource,
    private val locale: Locale,
    private val dates: DateWords
) {
    fun say(phrases: List<RepeatPhrase>): String {
        val (ends, head) = phrases.partition {
            it is RepeatPhrase.Times || it is RepeatPhrase.Until
        }
        return (listOf(head.joinToString(" ") { phrase(it) }) + ends.map { phrase(it) })
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }

    private fun phrase(phrase: RepeatPhrase): String = when (phrase) {
        is RepeatPhrase.Every -> every(phrase.frequency, phrase.interval)

        RepeatPhrase.EveryWeekday -> words.text(R.string.repeat_every_weekday)

        is RepeatPhrase.OnWeekdays ->
            words.text(R.string.repeat_on_weekdays, list(phrase.days.map { it.name() }))

        is RepeatPhrase.OnMonthDays -> monthDays(phrase.days)

        is RepeatPhrase.OnOrdinalWeekday ->
            words.text(R.string.repeat_on_ordinal, ordinal(phrase.ordinal), phrase.day.name())

        is RepeatPhrase.OnDate ->
            words.text(R.string.repeat_on_date, phrase.month.name(), phrase.day)

        is RepeatPhrase.InMonths ->
            words.text(R.string.repeat_in_months, list(phrase.months.map { it.name() }))

        is RepeatPhrase.OrdinalWeekdayOfMonth -> words.text(
            R.string.repeat_ordinal_of_month,
            ordinal(phrase.ordinal),
            phrase.day.name(),
            phrase.month.name()
        )

        is RepeatPhrase.Times -> words.plural(R.plurals.repeat_times, phrase.count, phrase.count)

        is RepeatPhrase.Until -> words.text(R.string.repeat_until, dates.date(phrase.date))

        RepeatPhrase.Custom -> words.text(R.string.repeat_custom)
    }

    private fun every(frequency: Frequency, interval: Int): String {
        val plural = when (frequency) {
            Frequency.DAILY -> R.plurals.repeat_daily
            Frequency.WEEKLY -> R.plurals.repeat_weekly
            Frequency.MONTHLY -> R.plurals.repeat_monthly
            Frequency.YEARLY -> R.plurals.repeat_yearly
        }
        return words.plural(plural, interval, interval)
    }

    private fun monthDays(days: List<Int>): String = if (days == listOf(LAST)) {
        words.text(R.string.repeat_on_last_day)
    } else {
        val names = days.map { if (it == LAST) words.text(R.string.repeat_last) else it.toString() }
        words.text(R.string.repeat_on_month_days, list(names))
    }

    private fun ordinal(position: Int): String = words.text(
        when (position) {
            FIRST -> R.string.repeat_ordinal_1
            SECOND -> R.string.repeat_ordinal_2
            THIRD -> R.string.repeat_ordinal_3
            FOURTH -> R.string.repeat_ordinal_4
            FIFTH -> R.string.repeat_ordinal_5
            else -> R.string.repeat_ordinal_last
        }
    )

    /** "Monday, Tuesday and Wednesday". */
    private fun list(items: List<String>): String = if (items.size < 2) {
        items.joinToString()
    } else {
        words.text(R.string.repeat_and, items.dropLast(1).joinToString(", "), items.last())
    }

    private fun DayOfWeek.name() = getDisplayName(TextStyle.FULL, locale)

    private fun Month.name() = getDisplayName(TextStyle.FULL, locale)

    private companion object {
        const val LAST = -1
        const val FIRST = 1
        const val SECOND = 2
        const val THIRD = 3
        const val FOURTH = 4
        const val FIFTH = 5
    }
}
