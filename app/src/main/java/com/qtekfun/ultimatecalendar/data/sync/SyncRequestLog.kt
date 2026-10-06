// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import android.content.SharedPreferences
import androidx.core.content.edit
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** When each account was last asked to sync, kept across process deaths (the job restarts it). */
interface SyncRequestLog {
    fun lastRequest(account: CalendarAccount): Instant?

    fun record(accounts: Collection<CalendarAccount>, at: Instant)
}

/** [SyncRequestLog] in its own SharedPreferences file, outside the settings and their backup. */
@Singleton
class PreferencesSyncRequestLog @Inject constructor(
    @Named(FILE) private val preferences: SharedPreferences
) : SyncRequestLog {
    override fun lastRequest(account: CalendarAccount): Instant? =
        preferences.getLong(key(account), NONE).takeIf { it != NONE }?.let(Instant::ofEpochMilli)

    override fun record(accounts: Collection<CalendarAccount>, at: Instant) {
        preferences.edit { accounts.forEach { putLong(key(it), at.toEpochMilli()) } }
    }

    private fun key(account: CalendarAccount) = "${account.type}|${account.name}"

    companion object {
        const val FILE = "sync_requests"
        private const val NONE = Long.MIN_VALUE
    }
}
