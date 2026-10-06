// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules

/** Moves the event to [calendar]; a color it cannot keep there goes back to the calendar's. */
fun EventForm.withCalendar(calendar: CalendarInfo): EventForm = copy(
    calendarId = calendar.id,
    color = color.takeIf { EventColorSupport.supports(calendar) }
)

fun EventForm.withReminder(reminder: Reminder): EventForm = when {
    reminder in reminders || reminders.size >= SettingsRules.MAX_REMINDERS -> this
    reminder.minutesBefore > SettingsRules.MAX_REMINDER_MINUTES -> this
    else -> copy(reminders = (reminders + reminder).sortedBy { it.minutesBefore })
}

fun EventForm.withoutReminder(reminder: Reminder): EventForm =
    copy(reminders = reminders - reminder)

fun EventForm.withRepeat(setting: RepeatSetting): EventForm = copy(repeat = setting)
