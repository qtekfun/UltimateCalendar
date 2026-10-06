// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.caldav

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.auth.Logout
import com.qtekfun.ultimatecalendar.sync.CalDavSync
import com.qtekfun.ultimatecalendar.sync.CalDavSyncScheduler
import com.qtekfun.ultimatecalendar.sync.engine.SyncOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What the account screen of T37 shows. */
sealed interface CalDavAccountState {
    data object SignedOut : CalDavAccountState

    /**
     * [calendars] is 0 until the first sync. [scheduling] tells whether the server sends the
     * invitations and answers itself (RFC 6638); without it, guests are only a list. [pending]
     * changes wait to be sent, [failed] of them were refused and wait for the user.
     */
    data class SignedIn(
        val account: SignedInAccount,
        val calendars: Int,
        val addresses: List<String>,
        val scheduling: Boolean,
        val pending: Int,
        val failed: Int
    ) : CalDavAccountState
}

/**
 * What the account UI (T37) uses besides `LoginFlow`: the account's state as a flow, "sync now",
 * how the last sync ended and signing out. Signing in is `LoginFlow`; the sync starts by itself
 * when the session changes (see `CalDavSync`).
 */
@Singleton
class CalDavAccountRepository @Inject constructor(
    private val session: AccountSession,
    private val database: UltimateCalendarDatabase,
    private val sync: CalDavSync,
    private val scheduler: CalDavSyncScheduler,
    private val logout: Logout,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    /** Re-read whenever the account, its calendars or its queue change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<CalDavAccountState> = session.activeAccount.flatMapLatest { signed ->
        if (signed == null) {
            flowOf(CalDavAccountState.SignedOut)
        } else {
            database.invalidationTracker
                .createFlow("dav_account", "dav_calendar", "pending_operation")
                .map { read(signed) }
        }
    }.flowOn(io)

    /** How the latest sync ended; null until one has. */
    val lastSync: StateFlow<SyncOutcome?> get() = sync.lastOutcome

    suspend fun syncNow(): SyncOutcome = sync.syncNow()

    /**
     * Revokes the app password where possible, forgets the login and deletes everything of the
     * account from this phone, including changes not sent yet.
     */
    suspend fun signOut() = withContext(io) {
        val signed = session.activeAccount.value
        logout()
        scheduler.cancelAll()
        signed?.let {
            database.davAccountDao().find(it.serverUrl, it.loginName)?.let { row ->
                database.davAccountDao().delete(row.id)
            }
        }
    }

    private suspend fun read(signed: SignedInAccount): CalDavAccountState {
        val row = database.davAccountDao().find(signed.serverUrl, signed.loginName)
            ?: return CalDavAccountState.SignedIn(signed, 0, emptyList(), false, 0, 0)
        val operations = database.pendingOperationDao().all(row.id)
        return CalDavAccountState.SignedIn(
            account = signed,
            calendars = database.davCalendarDao().all(row.id).size,
            addresses = CalDavMapping.addresses(row),
            scheduling = row.scheduling,
            pending = operations.count { !it.failed },
            failed = operations.count { it.failed }
        )
    }
}
