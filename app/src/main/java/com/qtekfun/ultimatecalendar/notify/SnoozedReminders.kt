// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.domain.reminders.PlannedReminder
import java.time.Instant

/** The postponed reminders, kept on this phone so they survive a restart (RF-07). */
interface SnoozedReminders {
    suspend fun all(): List<PlannedReminder>

    /** Keeps [reminder], replacing the one with its id. */
    suspend fun put(reminder: PlannedReminder)

    suspend fun remove(ids: Collection<Long>)

    /**
     * Removes the reminder with [id] if it is still there and still for [at], and says whether it
     * was: whoever gets true shows it, so a reminder never shows twice.
     */
    suspend fun take(id: Long, at: Instant): Boolean
}
