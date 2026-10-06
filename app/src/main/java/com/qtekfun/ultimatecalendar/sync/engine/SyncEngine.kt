// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity
import com.qtekfun.ultimatecalendar.data.remote.caldav.CalDav
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavResult
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.sync.queue.ProcessResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The signed-in account and its CalDAV access, as a sync needs them. */
class SyncSession(val account: SignedInAccount, val dav: CalDav)

/** Where a sync gets the account to sync from: null when nobody is signed in. */
fun interface SyncSource {
    suspend fun open(): SyncSession?
}

/** How a sync ended. */
sealed interface SyncOutcome {
    /** Pulled; [pushed] tells what happened to the queued changes. */
    data class Ok(val pushed: ProcessResult) : SyncOutcome

    data object NoAccount : SyncOutcome

    data object Offline : SyncOutcome

    data object Unauthorized : SyncOutcome

    data class Error(val reason: String) : SyncOutcome
}

/**
 * Syncs the signed-in account: first sends the queued local changes, then pulls the server
 * state, so the conflict resolver only sees real conflicts. Syncs never overlap: one started
 * while another runs waits for it. Nothing starts it yet; scheduling comes with the UI.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val source: SyncSource,
    private val database: UltimateCalendarDatabase,
    private val push: PushSync,
    private val pull: PullSync,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    private val mutex = Mutex()
    private val mutableLastOutcome = MutableStateFlow<SyncOutcome?>(null)

    /** How the latest sync of this process ended; null until one finishes. */
    val lastOutcome: StateFlow<SyncOutcome?> = mutableLastOutcome.asStateFlow()

    suspend fun sync(): SyncOutcome = withContext(dispatcher) {
        mutex.withLock { run().also { mutableLastOutcome.value = it } }
    }

    private suspend fun run(): SyncOutcome {
        val session = source.open() ?: return SyncOutcome.NoAccount
        val account = account(session.account)
        val pushed = push.push(session.dav, account.id)
        // The account row may have changed meanwhile (calendar home found), so read it again.
        val current = database.davAccountDao().get(account.id) ?: account
        return pull.pull(session.dav, current)?.toOutcome() ?: SyncOutcome.Ok(pushed)
    }

    private suspend fun account(signedIn: SignedInAccount): DavAccountEntity {
        val accounts = database.davAccountDao()
        return accounts.find(signedIn.serverUrl, signedIn.loginName)
            ?: DavAccountEntity(serverUrl = signedIn.serverUrl, loginName = signedIn.loginName)
                .let { it.copy(id = accounts.insert(it)) }
    }

    private fun DavResult<*>.toOutcome(): SyncOutcome = when (this) {
        is DavResult.NetworkError -> SyncOutcome.Offline
        DavResult.Unauthorized -> SyncOutcome.Unauthorized
        is DavResult.HttpError -> SyncOutcome.Error("HTTP $code")
        else -> SyncOutcome.Error(toString())
    }
}
