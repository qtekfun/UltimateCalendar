// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.sync.engine.SyncEngine
import com.qtekfun.ultimatecalendar.sync.engine.SyncOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the CalDAV syncs in step with the account: they are scheduled while somebody is signed in
 * and cancelled the moment nobody is, so that nothing runs, and nothing reaches the network,
 * without an account.
 */
@Singleton
class CalDavSync @Inject constructor(
    private val session: AccountSession,
    private val scheduler: CalDavSyncScheduler,
    private val engine: SyncEngine,
    @IoDispatcher private val io: CoroutineDispatcher
) : OwnAccountSync {
    /** How the latest sync of this process ended; null until one finishes. */
    val lastOutcome: StateFlow<SyncOutcome?> get() = engine.lastOutcome

    /** Whether a sync is running now. */
    val syncing: StateFlow<Boolean> get() = engine.syncing

    /** Follows the signed-in account until [scope] ends. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            withContext(io) { session.restore() }
            session.activeAccount.collect { account ->
                if (account == null) {
                    scheduler.cancelAll()
                } else {
                    scheduler.schedulePeriodic()
                    scheduler.syncNow()
                }
            }
        }
    }

    /** The user opened the app: sync soon, if there is an account. */
    fun onAppOpened() {
        if (session.activeAccount.value != null) scheduler.syncSoon()
    }

    /** Syncs now, in the caller's coroutine, and tells how it went ("check now" buttons). */
    override suspend fun syncNow(): SyncOutcome = engine.sync()
}
