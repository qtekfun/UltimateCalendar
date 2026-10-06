// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.data.settings.ThemeMode
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindOption
import com.qtekfun.ultimatecalendar.domain.settings.FirstDayOfWeek
import com.qtekfun.ultimatecalendar.domain.settings.InitialView
import com.qtekfun.ultimatecalendar.domain.settings.InviteCheckInterval
import com.qtekfun.ultimatecalendar.domain.settings.OffsetUnit
import com.qtekfun.ultimatecalendar.domain.settings.ReminderOffset
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Names of the choices, in the language of the UI. */
@Composable
internal fun themeName(theme: ThemeMode): String = stringResource(
    when (theme) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

@Composable
internal fun firstDayName(day: FirstDayOfWeek): String = stringResource(
    when (day) {
        FirstDayOfWeek.LOCALE -> R.string.first_day_locale
        FirstDayOfWeek.SATURDAY -> R.string.first_day_saturday
        FirstDayOfWeek.SUNDAY -> R.string.first_day_sunday
        FirstDayOfWeek.MONDAY -> R.string.first_day_monday
    }
)

@Composable
internal fun viewName(view: InitialView): String = stringResource(
    when (view) {
        InitialView.AGENDA -> R.string.view_agenda
        InitialView.DAY -> R.string.view_day
        InitialView.WEEK -> R.string.view_week
        InitialView.MONTH -> R.string.view_month
    }
)

@Composable
internal fun inviteCheckName(interval: InviteCheckInterval): String =
    interval.minutes?.let { pluralStringResource(R.plurals.invite_check_every, it, it) }
        ?: stringResource(R.string.invite_check_manual)

@Composable
internal fun reRemindName(option: ReRemindOption): String = stringResource(
    when (option) {
        ReRemindOption.OFF -> R.string.re_remind_off
        ReRemindOption.DAY_BEFORE -> R.string.re_remind_day_before
        ReRemindOption.HOUR_BEFORE -> R.string.re_remind_hour_before
        ReRemindOption.BOTH -> R.string.re_remind_both
    }
)

@Composable
internal fun durationName(minutes: Int): String =
    pluralStringResource(R.plurals.duration_minutes, minutes, minutes)

@Composable
internal fun missedWindowName(hours: Int): String = if (hours == 0) {
    stringResource(R.string.settings_missed_window_off)
} else {
    pluralStringResource(R.plurals.settings_missed_window_hours, hours, hours)
}

@Composable
internal fun reminderName(minutes: Int): String {
    val offset = ReminderOffset.of(minutes)
    val plural = when (offset.unit) {
        OffsetUnit.AT_START -> return stringResource(R.string.reminder_offset_at_start)
        OffsetUnit.MINUTES -> R.plurals.reminder_offset_minutes
        OffsetUnit.HOURS -> R.plurals.reminder_offset_hours
        OffsetUnit.DAYS -> R.plurals.reminder_offset_days
        OffsetUnit.WEEKS -> R.plurals.reminder_offset_weeks
    }
    return pluralStringResource(plural, offset.count, offset.count)
}

@Composable
internal fun timeOfDayName(minuteOfDay: Int): String = DateTimeFormatter.ofLocalizedTime(
    FormatStyle.SHORT
).format(LocalTime.ofSecondOfDay(minuteOfDay * SECONDS_PER_MINUTE))

private const val SECONDS_PER_MINUTE = 60L
