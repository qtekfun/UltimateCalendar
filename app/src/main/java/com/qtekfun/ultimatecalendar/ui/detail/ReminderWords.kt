// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.detail.ReminderLine
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import com.qtekfun.ultimatecalendar.domain.settings.OffsetUnit
import com.qtekfun.ultimatecalendar.domain.settings.ReminderOffset

/** A reminder in words, with the plural of its unit and how it reaches the user. */
internal class ReminderWords(private val words: WordSource, private val dates: DateWords) {
    fun say(line: ReminderLine): String {
        val base = line.allDay?.let { allDay ->
            if (allDay.daysBefore == 0) {
                words.text(R.string.detail_reminder_all_day_same_day, dates.time(allDay.at))
            } else {
                words.plural(
                    R.plurals.detail_reminder_all_day,
                    allDay.daysBefore,
                    allDay.daysBefore,
                    dates.time(allDay.at)
                )
            }
        } ?: offset(line.minutesBefore)
        return when (line.method) {
            ReminderMethod.ALERT -> base
            ReminderMethod.EMAIL -> words.text(R.string.detail_reminder_email, base)
            ReminderMethod.SMS -> words.text(R.string.detail_reminder_sms, base)
        }
    }

    private fun offset(minutes: Int): String {
        val offset = ReminderOffset.of(minutes)
        val plural = when (offset.unit) {
            OffsetUnit.AT_START -> return words.text(R.string.reminder_offset_at_start)
            OffsetUnit.MINUTES -> R.plurals.reminder_offset_minutes
            OffsetUnit.HOURS -> R.plurals.reminder_offset_hours
            OffsetUnit.DAYS -> R.plurals.reminder_offset_days
            OffsetUnit.WEEKS -> R.plurals.reminder_offset_weeks
        }
        return words.plural(plural, offset.count, offset.count)
    }
}
