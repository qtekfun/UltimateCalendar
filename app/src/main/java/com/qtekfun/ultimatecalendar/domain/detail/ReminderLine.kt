// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.detail

import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import java.time.LocalTime

/**
 * A reminder as the detail says it. For an all-day event the provider counts the minutes back
 * from midnight of its first day, so "9:00 the day before" is 900 minutes: [allDay] turns that
 * into days before and a time of day; it is null for timed events.
 */
data class ReminderLine(
    val minutesBefore: Int,
    val method: ReminderMethod,
    val allDay: AllDayOffset? = null
) {
    /** [daysBefore] the first day of the event, at [at]; 0 days is the day itself. */
    data class AllDayOffset(val daysBefore: Int, val at: LocalTime)

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60

        private const val SECONDS_PER_MINUTE = 60L

        /** The lines for [reminders], the one closest to the start first. */
        fun of(reminders: List<Reminder>, allDay: Boolean): List<ReminderLine> = reminders
            .sortedBy { it.minutesBefore }
            .map { ReminderLine(it.minutesBefore, it.method, if (allDay) offset(it) else null) }

        private fun offset(reminder: Reminder): AllDayOffset {
            val days = (reminder.minutesBefore + MINUTES_PER_DAY - 1) / MINUTES_PER_DAY
            val minuteOfDay = days * MINUTES_PER_DAY - reminder.minutesBefore
            return AllDayOffset(days, LocalTime.ofSecondOfDay(minuteOfDay * SECONDS_PER_MINUTE))
        }
    }
}
