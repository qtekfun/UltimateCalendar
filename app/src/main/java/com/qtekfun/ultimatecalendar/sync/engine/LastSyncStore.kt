// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * When the CalDAV account last synced successfully, kept across restarts so that the account
 * screen can tell "3 hours ago" even in a fresh process. Not a secret, and gone with the account.
 */
interface LastSyncStore {
    fun lastOk(): Instant?

    fun recordOk(at: Instant)

    fun clear()
}

/** [LastSyncStore] in its own SharedPreferences file. */
@Singleton
class PreferencesLastSyncStore @Inject constructor(
    @Named(FILE) private val preferences: SharedPreferences
) : LastSyncStore {
    override fun lastOk(): Instant? = preferences.getLong(LAST_OK, NONE).takeIf { it != NONE }
        ?.let(Instant::ofEpochMilli)

    override fun recordOk(at: Instant) = preferences.edit { putLong(LAST_OK, at.toEpochMilli()) }

    override fun clear() = preferences.edit { remove(LAST_OK) }

    companion object {
        const val FILE = "caldav_sync_state"
        private const val LAST_OK = "last_ok"
        private const val NONE = -1L
    }
}
