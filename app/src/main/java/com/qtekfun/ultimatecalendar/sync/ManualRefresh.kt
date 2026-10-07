// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionsRefresh
import com.qtekfun.ultimatecalendar.data.sync.SyncEnvironment
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.refresh.RefreshIssue
import com.qtekfun.ultimatecalendar.domain.refresh.RefreshReport
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.sync.engine.SyncOutcome
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The refresh button (RF-06): the user asks for everything to be as new as it can be, so every
 * part is started on its manual path, all at once:
 *
 * - the invitation check with an expedited, unlimited request for every account of the phone
 *   that can sync (`SyncRequestPolicy`: only an account that cannot sync at all is skipped), which
 *   also reads the phone's calendars again;
 * - the app's own CalDAV account, synced now and waited for;
 * - the subscriptions, downloaded again and waited for.
 *
 * The parts that need the network are not started without it, so offline the answer comes at
 * once ([RefreshIssue.OFFLINE]) and the phone's calendars are still read again. Each waited-for
 * part has a time limit, so the button can never stay busy. A second tap while one refresh runs
 * does nothing. The parts are independent: one that fails does not cancel the others, which would
 * leave a check half done.
 */
@Singleton
@Suppress("LongParameterList")
class ManualRefresh @Inject constructor(
    private val checker: InvitationChecker,
    private val source: CalendarSource,
    private val own: OwnAccountSync,
    private val subscriptions: SubscriptionsRefresh,
    private val environment: SyncEnvironment,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    private val busy = MutableStateFlow(false)

    /** Whether a refresh is running now. */
    val running: StateFlow<Boolean> = busy.asStateFlow()

    /** Refreshes everything and tells how it went; null if one was already running. */
    suspend fun refresh(): RefreshReport? {
        if (!busy.compareAndSet(expect = false, update = true)) return null
        return try {
            withContext(io) { run() }
        } finally {
            busy.value = false
        }
    }

    private suspend fun run(): RefreshReport = supervisorScope {
        val online = environment.device().networkAvailable
        val check = async { checker.check(requestSync = true, manual = true) }
        val dav = async { if (online) withTimeoutOrNull(LIMIT_MS) { own.syncNow() } else null }
        val feeds = async {
            if (online) withTimeoutOrNull(LIMIT_MS) { subscriptions.refreshAll() } else null
        }
        val cannotSync = async { accountsThatCannotSync() }
        val issues = linkedSetOf<RefreshIssue>()
        if (!online) issues += RefreshIssue.OFFLINE
        if (cannotSync.await()) issues += RefreshIssue.ACCOUNT_SYNC_OFF
        when (val outcome = check.await()) {
            is InvitationCheckOutcome.Done -> Unit
            is InvitationCheckOutcome.Failed -> issues += outcome.error.issue()
        }
        val account = dav.await()
        val downloaded = feeds.await()
        if (online && (account == null || downloaded == null)) issues += RefreshIssue.SERVER_ERROR
        account?.issue()?.let(issues::add)
        if (downloaded != null && downloaded.temporaryFailures > 0) {
            issues += RefreshIssue.SERVER_ERROR
        }
        RefreshReport(issues)
    }

    /** Whether any account of the Android provider says it cannot sync calendars at all. */
    private suspend fun accountsThatCannotSync(): Boolean {
        val calendars = (source.calendars() as? CalendarResult.Success)?.value ?: return false
        return calendars.map { it.account }.distinct().filter { it.isAndroidAccount() }
            .any { !environment.account(it).syncable }
    }

    private fun CalendarAccount.isAndroidAccount() = !isLocal && !isCalDav && !isSubscription

    private fun CalendarError.issue() = when (this) {
        CalendarError.PermissionDenied -> RefreshIssue.PERMISSION_MISSING
        else -> RefreshIssue.READ_FAILED
    }

    private fun SyncOutcome.issue(): RefreshIssue? = when (this) {
        is SyncOutcome.Ok, SyncOutcome.NoAccount -> null
        SyncOutcome.Offline -> RefreshIssue.OFFLINE
        SyncOutcome.Unauthorized -> RefreshIssue.SIGN_IN_REFUSED
        is SyncOutcome.Error -> RefreshIssue.SERVER_ERROR
    }

    private companion object {
        /** The longest a refresh waits for a server. */
        val LIMIT_MS = Duration.ofSeconds(45).toMillis()
    }
}
