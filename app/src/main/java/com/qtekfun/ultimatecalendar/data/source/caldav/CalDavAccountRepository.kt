// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.caldav

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.source.CalDavIds
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.auth.Logout
import com.qtekfun.ultimatecalendar.domain.caldav.CalDavCalendarItem
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.sync.CalDavSync
import com.qtekfun.ultimatecalendar.sync.CalDavSyncScheduler
import com.qtekfun.ultimatecalendar.sync.engine.LastSyncStore
import com.qtekfun.ultimatecalendar.sync.engine.SyncOutcome
import com.qtekfun.ultimatecalendar.sync.engine.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

/** Runs after the account's data left this phone: what keeps its own copy must forget it too. */
fun interface AccountRemovedListener {
    suspend fun accountRemoved()
}

/**
 * What the account UI (T37) uses besides `LoginFlow`: the account's state as a flow, its
 * calendars with the switch that turns each on or off, "sync now", how the last sync ended and
 * signing out. Signing in is `LoginFlow`; the sync starts by itself when the session changes
 * (see `CalDavSync`).
 */
@Singleton
@Suppress("LongParameterList")
class CalDavAccountRepository @Inject constructor(
    private val session: AccountSession,
    private val database: UltimateCalendarDatabase,
    private val sync: CalDavSync,
    private val scheduler: CalDavSyncScheduler,
    private val logout: Logout,
    private val lastSyncStore: LastSyncStore,
    private val removed: AccountRemovedListener,
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

    /** What to tell the user about the sync: running, fine, offline, refused or failed. */
    val syncStatus: Flow<SyncStatus> = combine(sync.lastOutcome, sync.syncing) { outcome, syncing ->
        SyncStatus.of(outcome, syncing, lastSyncStore.lastOk())
    }

    /**
     * The calendars of the signed-in account in the server's order, with whether each is on.
     * Empty before the first sync and when nobody is signed in.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val calendars: Flow<List<CalDavCalendarItem>> = session.activeAccount.flatMapLatest { signed ->
        if (signed == null) {
            flowOf(emptyList())
        } else {
            database.invalidationTracker
                .createFlow("dav_account", "dav_calendar", "calendar_settings")
                .map { readCalendars(signed) }
        }
    }.flowOn(io)

    /** A manual sync: it runs now, whatever the schedule says, and tells how it went. */
    suspend fun syncNow(): SyncOutcome = sync.syncNow()

    /**
     * Turns a calendar on or off on this phone only (the server is never changed). Off, it
     * stops syncing and leaves the views, through the visibility override of RF-02; its events
     * stay stored and come back, up to date, when it is turned on again.
     */
    suspend fun setCalendarEnabled(id: CalendarId, enabled: Boolean) = withContext(io) {
        val dao = database.calendarSettingsDao()
        val old = dao.find(id.value) ?: CalendarSettingsEntity(id.value, null, null, null)
        val updated = old.copy(visible = if (enabled) null else false)
        if (updated.displayName == null && updated.color == null && updated.visible == null) {
            dao.clear(id.value)
        } else {
            dao.save(updated)
        }
        if (enabled) scheduler.syncSoon()
    }

    /**
     * Revokes the app password where possible, forgets the login and deletes everything of the
     * account from this phone, including changes not sent yet: its calendars, events and queue,
     * what only lived here for its calendars (names, colors, visibility, the default), the
     * invitations noticed and their re-reminders. The syncs stop, and the reminders are planned
     * again without its events.
     */
    suspend fun signOut() {
        // Leaving the screen must not stop it halfway: the login would be gone and the data not.
        withContext(io + NonCancellable) {
            val signed = session.activeAccount.value
            logout()
            scheduler.cancelAll()
            signed?.let { deleteLocalData(it) }
            lastSyncStore.clear()
        }
        removed.accountRemoved()
    }

    private suspend fun deleteLocalData(signed: SignedInAccount) {
        // Overrides restored for calendars that never got to exist wait under the account's name.
        database.accountCleanupDao().clearPendingOverrides(
            CalDavMapping.accountName(signed.serverUrl, signed.loginName)
        )
        val row = database.davAccountDao().find(signed.serverUrl, signed.loginName) ?: return
        val ids = database.davCalendarDao().all(row.id).map { CalDavIds.encode(it.id) }
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                if (ids.isNotEmpty()) {
                    val cleanup = database.accountCleanupDao()
                    cleanup.clearSettings(ids)
                    cleanup.clearNotified(ids)
                    cleanup.clearAttended(ids)
                    cleanup.clearReReminders(ids)
                }
                // Calendars, events and the queue go with the account (foreign keys).
                database.davAccountDao().delete(row.id)
            }
        }
    }

    private suspend fun readCalendars(signed: SignedInAccount): List<CalDavCalendarItem> {
        val row = database.davAccountDao().find(signed.serverUrl, signed.loginName)
            ?: return emptyList()
        val off = database.calendarSettingsDao().all()
            .filter { it.visible == false }
            .map { it.calendarId }
            .toSet()
        return database.davCalendarDao().all(row.id)
            .sortedWith(
                compareBy({ it.sortOrder == null }, { it.sortOrder }, { it.name.lowercase() })
            )
            .map {
                val id = CalDavIds.calendar(it.id)
                CalDavCalendarItem(
                    id = id,
                    name = it.name,
                    color = CalDavMapping.color(it.color),
                    writable = it.writable,
                    enabled = id.value !in off
                )
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
