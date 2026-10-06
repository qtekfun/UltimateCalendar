// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Reminders
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod

/** `Reminders` rows to [Reminder] and back. */
internal object ReminderMapping {
    val projection = listOf(Reminders._ID, Reminders.MINUTES, Reminders.METHOD)

    /** `METHOD_*`; the calendar's default and the legacy "alarm" are plain alerts. */
    fun methodOf(code: Int?): ReminderMethod = when (code) {
        Reminders.METHOD_EMAIL -> ReminderMethod.EMAIL
        Reminders.METHOD_SMS -> ReminderMethod.SMS
        else -> ReminderMethod.ALERT
    }

    fun methodCode(method: ReminderMethod): Int = when (method) {
        ReminderMethod.ALERT -> Reminders.METHOD_ALERT
        ReminderMethod.EMAIL -> Reminders.METHOD_EMAIL
        ReminderMethod.SMS -> Reminders.METHOD_SMS
    }

    /**
     * The reminder of [row], or null for "the calendar's default" (`MINUTES_DEFAULT`, a negative
     * number), which only the provider can resolve.
     */
    fun toReminder(row: ProviderRow): Reminder? {
        val minutes = row.int(Reminders.MINUTES)
        return minutes?.takeIf {
            it >= 0
        }?.let { Reminder(it, methodOf(row.int(Reminders.METHOD))) }
    }

    /** Whether [row] is "the calendar's default" (`MINUTES_DEFAULT`), which [toReminder] skips. */
    fun isDefault(row: ProviderRow): Boolean = (row.int(Reminders.MINUTES) ?: 0) < 0

    /** The row to insert for [reminder]; [eventId] is null when a batch back reference sets it. */
    fun toValues(reminder: Reminder, eventId: Long?): ProviderRow = buildMap {
        eventId?.let { put(Reminders.EVENT_ID, it) }
        put(Reminders.MINUTES, reminder.minutesBefore)
        put(Reminders.METHOD, methodCode(reminder.method))
    }
}
