// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.content.Context
import androidx.core.content.edit
import com.qtekfun.ultimatecalendar.domain.reminders.ShownReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [ShownReminders] in SharedPreferences, one `id|epochMillis` entry each. A few hundred entries
 * at most: nothing older than the longest window is kept.
 */
@Singleton
class PreferencesShownReminders @Inject constructor(@ApplicationContext context: Context) :
    ShownReminders {
    // The alarm, the heartbeat and the start all write: one at a time keeps each entry.
    private val lock = Mutex()
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override suspend fun all(): Set<ShownReminder> = lock.withLock { read() }

    override suspend fun add(reminders: Collection<ShownReminder>) =
        lock.withLock { write(read() + reminders) }

    override suspend fun forgetBefore(instant: Instant) = lock.withLock {
        write(read().filterTo(mutableSetOf()) { !it.at.isBefore(instant) })
    }

    override suspend fun baselineDone(): Boolean =
        lock.withLock { preferences.getBoolean(KEY_BASELINE, false) }

    override suspend fun setBaselineDone() =
        lock.withLock { preferences.edit { putBoolean(KEY_BASELINE, true) } }

    private fun read(): Set<ShownReminder> = preferences.getStringSet(KEY_SHOWN, emptySet())
        .orEmpty()
        .mapNotNull(::parse)
        .toSet()

    private fun parse(entry: String): ShownReminder? {
        val parts = entry.split(SEPARATOR)
        val id = parts.getOrNull(0)?.toLongOrNull()
        val millis = parts.getOrNull(1)?.toLongOrNull()
        return if (parts.size == 2 && id != null && millis != null) {
            ShownReminder(id, Instant.ofEpochMilli(millis))
        } else {
            null
        }
    }

    private fun write(reminders: Set<ShownReminder>) = preferences.edit {
        putStringSet(
            KEY_SHOWN,
            reminders.mapTo(mutableSetOf()) { "${it.reminderId}$SEPARATOR${it.at.toEpochMilli()}" }
        )
    }

    private companion object {
        const val PREFERENCES = "shown_reminders"
        const val KEY_SHOWN = "shown"
        const val KEY_BASELINE = "baseline"
        const val SEPARATOR = "|"
    }
}
