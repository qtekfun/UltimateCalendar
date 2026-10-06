// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.content.Context
import androidx.core.content.edit
import com.qtekfun.ultimatecalendar.domain.reminders.PlannedReminder
import com.qtekfun.ultimatecalendar.domain.reminders.SnoozeCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** [SnoozedReminders] in SharedPreferences, one [SnoozeCodec] line each. A handful at most. */
@Singleton
class PreferencesSnoozedReminders @Inject constructor(@ApplicationContext context: Context) :
    SnoozedReminders {
    // The notification buttons, the alarms and the recovery all use it: one at a time.
    private val lock = Mutex()
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override suspend fun all(): List<PlannedReminder> = lock.withLock { read() }

    override suspend fun put(reminder: PlannedReminder) =
        lock.withLock { write(read().filter { it.id != reminder.id } + reminder) }

    override suspend fun remove(ids: Collection<Long>) =
        lock.withLock { write(read().filter { it.id !in ids }) }

    override suspend fun take(id: Long, at: Instant): Boolean = lock.withLock {
        val current = read()
        val found = current.any { it.id == id && it.at == at }
        if (found) write(current.filter { it.id != id })
        found
    }

    private fun read(): List<PlannedReminder> = preferences.getStringSet(KEY, emptySet())
        .orEmpty()
        .mapNotNull(SnoozeCodec::decode)

    private fun write(reminders: List<PlannedReminder>) = preferences.edit {
        putStringSet(KEY, reminders.mapTo(mutableSetOf(), SnoozeCodec::encode))
    }

    private companion object {
        const val PREFERENCES = "snoozed_reminders"
        const val KEY = "snoozed"
    }
}
