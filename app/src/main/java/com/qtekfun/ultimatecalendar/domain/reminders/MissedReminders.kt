// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import java.time.Duration
import java.time.Instant

/**
 * Reminders whose time passed without showing, because the system stopped the app and its
 * alarms (RF-08). Only those within [window] count: an older one would be noise, not a reminder.
 */
object MissedReminders {
    fun pick(
        planned: List<PlannedReminder>,
        shown: Set<ShownReminder>,
        now: Instant,
        window: Duration
    ): List<PlannedReminder> {
        val since = now.minus(window)
        return planned.filter { reminder ->
            !reminder.at.isAfter(now) && reminder.at.isAfter(since) &&
                ShownReminder(reminder.id, reminder.at) !in shown
        }.sortedBy { it.at }
    }

    /** Records older than this can go: no reminder that old is picked any more. */
    fun keepAfter(now: Instant, window: Duration): Instant = now.minus(window)
}
