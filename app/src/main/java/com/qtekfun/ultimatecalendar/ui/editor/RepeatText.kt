// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.editor.MonthlyDay
import com.qtekfun.ultimatecalendar.domain.editor.RepeatSetting
import com.qtekfun.ultimatecalendar.domain.editor.RepeatSummary
import com.qtekfun.ultimatecalendar.domain.recurrence.Frequency
import com.qtekfun.ultimatecalendar.domain.recurrence.RepeatEnd
import com.qtekfun.ultimatecalendar.domain.recurrence.RepeatPreset
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private const val LAST = -1

@Composable
internal fun presetName(preset: RepeatPreset): String = stringResource(
    when (preset) {
        RepeatPreset.NEVER -> R.string.editor_repeat_never
        RepeatPreset.DAILY -> R.string.editor_repeat_daily
        RepeatPreset.WEEKDAYS -> R.string.editor_repeat_weekdays
        RepeatPreset.WEEKLY -> R.string.editor_repeat_weekly
        RepeatPreset.BIWEEKLY -> R.string.editor_repeat_biweekly
        RepeatPreset.MONTHLY -> R.string.editor_repeat_monthly
        RepeatPreset.QUARTERLY -> R.string.editor_repeat_quarterly
        RepeatPreset.HALF_YEARLY -> R.string.editor_repeat_half_yearly
        RepeatPreset.YEARLY -> R.string.editor_repeat_yearly
    }
)

/** The line shown on the Repeat row: the preset's name, or a sentence for a custom rule. */
@Composable
internal fun repeatText(setting: RepeatSetting, anchor: LocalDate): String = when (setting) {
    is RepeatSetting.Preset -> presetName(setting.preset)
    is RepeatSetting.Custom -> summaryText(RepeatSummary.of(setting.repeat, anchor))
    is RepeatSetting.Unsupported -> stringResource(R.string.editor_repeat_unsupported)
}

@Composable
internal fun weekdayName(day: DayOfWeek, style: TextStyle = TextStyle.FULL): String =
    day.getDisplayName(style, currentLocale())

@Composable
internal fun monthlyDayText(day: MonthlyDay): String = when (day) {
    is MonthlyDay.OfMonth -> stringResource(R.string.editor_custom_monthly_day, day.day)

    is MonthlyDay.Nth -> stringResource(
        R.string.editor_custom_monthly_nth,
        ordinalName(day.ordinal),
        weekdayName(day.weekday)
    )
}

@Composable
private fun ordinalName(ordinal: Int): String = stringResource(
    when (ordinal) {
        LAST -> R.string.editor_ordinal_last
        2 -> R.string.editor_ordinal_2
        3 -> R.string.editor_ordinal_3
        4 -> R.string.editor_ordinal_4
        else -> R.string.editor_ordinal_1
    }
)

/** "Every 2 weeks on Monday, Wednesday, until Dec 1, 2026". */
@Composable
internal fun summaryText(summary: RepeatSummary): String {
    val n = summary.interval
    val base = when (summary.frequency) {
        Frequency.DAILY -> pluralStringResource(R.plurals.editor_sum_daily, n, n)

        Frequency.WEEKLY -> {
            val locale = currentLocale()
            pluralStringResource(
                R.plurals.editor_sum_weekly,
                n,
                n,
                summary.weekdays.joinToString(", ") { it.getDisplayName(TextStyle.FULL, locale) }
            )
        }

        Frequency.MONTHLY -> monthlySentence(summary)

        Frequency.YEARLY -> pluralStringResource(
            R.plurals.editor_sum_yearly,
            n,
            n,
            monthDayText(requireNotNull(summary.yearlyMonth), requireNotNull(summary.yearlyDay))
        )
    }
    return when (summary.end) {
        RepeatEnd.NEVER -> base

        RepeatEnd.ON_DATE -> summary.until?.let {
            stringResource(R.string.editor_sum_until, base, formatDate(it))
        } ?: base

        RepeatEnd.AFTER_COUNT -> stringResource(
            R.string.editor_sum_count,
            base,
            pluralStringResource(R.plurals.editor_times, summary.count, summary.count)
        )
    }
}

@Composable
private fun monthlySentence(summary: RepeatSummary): String {
    val n = summary.interval
    return when (val day = summary.monthly) {
        is MonthlyDay.Nth -> pluralStringResource(
            R.plurals.editor_sum_monthly_nth,
            n,
            n,
            ordinalName(day.ordinal),
            weekdayName(day.weekday)
        )

        is MonthlyDay.OfMonth -> pluralStringResource(
            R.plurals.editor_sum_monthly_day,
            n,
            n,
            day.day
        )

        null -> ""
    }
}

@Composable
private fun monthDayText(month: java.time.Month, day: Int): String {
    val locale = currentLocale()
    val pattern = DateFormat.getBestDateTimePattern(locale, "MMMMd")
    return DateTimeFormatter.ofPattern(pattern, locale).format(MonthDay.of(month, day))
}
