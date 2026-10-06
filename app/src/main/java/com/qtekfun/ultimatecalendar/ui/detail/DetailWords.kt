// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.qtekfun.ultimatecalendar.domain.detail.DetailTime
import com.qtekfun.ultimatecalendar.domain.detail.ReminderLine
import com.qtekfun.ultimatecalendar.domain.detail.RepeatPhrase
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Where the words come from: Android resources, or a table of strings in a unit test. */
interface WordSource {
    fun text(@StringRes id: Int, vararg args: Any): String

    fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any): String
}

/** Words from the app's resources, in the language of [resources]. */
class ResourceWords(private val resources: Resources) : WordSource {
    override fun text(id: Int, vararg args: Any): String = resources.getString(id, *args)

    override fun plural(id: Int, quantity: Int, vararg args: Any): String =
        resources.getQuantityString(id, quantity, *args)
}

/** Dates and times in the language of [locale]. */
internal class DateWords(private val locale: Locale) {
    fun date(date: LocalDate, style: FormatStyle = FormatStyle.MEDIUM): String =
        DateTimeFormatter.ofLocalizedDate(style).withLocale(locale).format(date)

    fun time(time: LocalTime): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time)

    fun dateTime(moment: ZonedDateTime): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale).format(moment)
}

/**
 * Puts the detail's pieces in words, in the language of [locale] and of the [WordSource]: how
 * an event repeats, when it is and what its reminders say. Plurals go through the source so
 * each language applies its own rules.
 */
class DetailWords(words: WordSource, locale: Locale) {
    private val dates = DateWords(locale)
    private val repeating = RepeatWords(words, locale, dates)
    private val timing = TimeWords(words, locale, dates)
    private val reminding = ReminderWords(words, dates)

    /** "Weekly on Monday and Wednesday, 5 times". */
    fun repeat(phrases: List<RepeatPhrase>): String = repeating.say(phrases)

    /** The day(s) and hours of an occurrence, one line per element. */
    fun whenLines(time: DetailTime): List<String> = timing.lines(time)

    /** "10 minutes before", "1 day before, at 9:00 AM". */
    fun reminder(line: ReminderLine): String = reminding.say(line)
}
