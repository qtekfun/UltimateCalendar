// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.editor.EventColorChoice
import com.qtekfun.ultimatecalendar.domain.editor.FormIssue
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.settings.OffsetUnit
import com.qtekfun.ultimatecalendar.domain.settings.ReminderOffset
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The locale of the UI, which changes with the app language. */
@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
internal fun formatDate(date: LocalDate): String {
    val locale = currentLocale()
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEMMMdyyyy")
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/** The time as the phone writes it: 24-hour or AM/PM, following the system setting. */
@Composable
internal fun formatTime(time: LocalTime): String {
    val locale = currentLocale()
    val pattern = DateFormat.getBestDateTimePattern(
        locale,
        if (DateFormat.is24HourFormat(LocalContext.current)) "Hm" else "hm"
    )
    return DateTimeFormatter.ofPattern(pattern, locale).format(time)
}

@Composable
internal fun formatTimeOfDay(minuteOfDay: Int): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(currentLocale())
        .format(LocalTime.ofSecondOfDay(minuteOfDay * SECONDS_PER_MINUTE))

private const val SECONDS_PER_MINUTE = 60L

@Composable
internal fun availabilityName(availability: Availability): String = stringResource(
    when (availability) {
        Availability.BUSY -> R.string.editor_busy
        Availability.FREE -> R.string.editor_free
        Availability.TENTATIVE -> R.string.editor_tentative
    }
)

@Composable
internal fun colorName(choice: EventColorChoice): String = stringResource(
    when (choice) {
        EventColorChoice.TOMATO -> R.string.editor_color_tomato
        EventColorChoice.FLAMINGO -> R.string.editor_color_flamingo
        EventColorChoice.TANGERINE -> R.string.editor_color_tangerine
        EventColorChoice.BANANA -> R.string.editor_color_banana
        EventColorChoice.SAGE -> R.string.editor_color_sage
        EventColorChoice.BASIL -> R.string.editor_color_basil
        EventColorChoice.PEACOCK -> R.string.editor_color_peacock
        EventColorChoice.BLUEBERRY -> R.string.editor_color_blueberry
        EventColorChoice.LAVENDER -> R.string.editor_color_lavender
        EventColorChoice.GRAPE -> R.string.editor_color_grape
        EventColorChoice.GRAPHITE -> R.string.editor_color_graphite
    }
)

/** How a reminder reads: "10 minutes before", or for all-day events "On the day". */
@Composable
internal fun reminderText(reminder: Reminder, allDay: Boolean): String {
    val offset = ReminderOffset.of(reminder.minutesBefore)
    val base = when {
        allDay && reminder.minutesBefore == 0 -> stringResource(R.string.editor_reminder_on_day)
        offset.unit == OffsetUnit.AT_START -> stringResource(R.string.reminder_offset_at_start)

        else -> pluralStringResource(
            when (offset.unit) {
                OffsetUnit.MINUTES -> R.plurals.reminder_offset_minutes
                OffsetUnit.HOURS -> R.plurals.reminder_offset_hours
                OffsetUnit.DAYS -> R.plurals.reminder_offset_days
                else -> R.plurals.reminder_offset_weeks
            },
            offset.count,
            offset.count
        )
    }
    return when (reminder.method) {
        ReminderMethod.ALERT -> base
        ReminderMethod.EMAIL -> stringResource(R.string.editor_reminder_email, base)
        ReminderMethod.SMS -> stringResource(R.string.editor_reminder_sms, base)
    }
}

@Composable
internal fun issueText(issue: FormIssue): String = stringResource(
    when (issue) {
        FormIssue.END_BEFORE_START -> R.string.editor_issue_end_before_start
        FormIssue.NO_CALENDAR -> R.string.editor_issue_no_calendar
        FormIssue.REPEAT_ENDS_BEFORE_START -> R.string.editor_issue_repeat_before_start
    }
)

/** Why a save failed, in words that tell the user what to do; never the technical reason. */
@Composable
internal fun saveErrorText(error: CalendarError): String = stringResource(
    when (error) {
        CalendarError.PermissionDenied -> R.string.editor_error_permission
        CalendarError.ReadOnly -> R.string.editor_error_read_only
        CalendarError.NotFound -> R.string.editor_error_not_found
        is CalendarError.Invalid -> R.string.editor_error_invalid
        is CalendarError.SourceFailure -> R.string.editor_error_failure
    }
)

@Composable
internal fun failureText(reason: LoadFailure): String = stringResource(
    when (reason) {
        LoadFailure.NO_CALENDAR -> R.string.editor_failed_no_calendar
        LoadFailure.NOT_FOUND -> R.string.editor_failed_not_found
        LoadFailure.READ_ONLY -> R.string.editor_failed_read_only
        LoadFailure.PERMISSION_DENIED -> R.string.editor_failed_permission
        LoadFailure.SOURCE_FAILURE -> R.string.editor_failed_source
    }
)
