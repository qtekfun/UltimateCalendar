// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

enum class ReminderMethod { ALERT, EMAIL, SMS }

/** A reminder [minutesBefore] the start of an event (`Reminders.MINUTES`). */
data class Reminder(val minutesBefore: Int, val method: ReminderMethod = ReminderMethod.ALERT) {
    init {
        require(minutesBefore >= 0) { "A reminder cannot come after the event starts" }
    }
}
