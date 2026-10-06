// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The occurrences that may remind between [from] and [to], with their reminders; emits again
 * when they change. The calendar sources (T04, T05) implement it, so no `ContentResolver` or
 * network reaches the reminders.
 */
fun interface ReminderEventSource {
    fun observe(from: Instant, to: Instant): Flow<List<EventReminders>>
}
