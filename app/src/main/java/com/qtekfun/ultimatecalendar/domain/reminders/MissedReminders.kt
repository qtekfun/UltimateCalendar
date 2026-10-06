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
            !reminder.at.isAfter(now) && reminder.at.isAfter(since) && !wasShown(reminder, shown)
        }.sortedBy { it.at }
    }

    /**
     * Whether [reminder] already showed. An all-day one is the same reminder in any zone, though
     * it goes off at another instant after the phone moved, so only its id counts; any other is
     * the same reminder only at the same time.
     */
    fun wasShown(reminder: PlannedReminder, shown: Set<ShownReminder>): Boolean =
        ShownReminder(reminder.id, reminder.at) in shown ||
            (reminder.allDay && shown.any { it.reminderId == reminder.id })

    /** Records older than this can go: no reminder that old is picked any more. */
    fun keepAfter(now: Instant, window: Duration): Instant = now.minus(window)
}
