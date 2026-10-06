// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.sync.ProviderSyncRequester
import com.qtekfun.ultimatecalendar.data.sync.SourceSyncRequester
import com.qtekfun.ultimatecalendar.data.sync.SyncEnvironment
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import com.qtekfun.ultimatecalendar.data.sync.SyncRequestLog
import com.qtekfun.ultimatecalendar.data.sync.SyncRequests
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Passes to the [ProviderSyncRequester] only the accounts the [SyncRequestPolicy] allows now and
 * remembers when it did, so the periodic check does not wake the sync adapters every run.
 */
class ThrottledSyncRequester @Inject constructor(
    private val delegate: ProviderSyncRequester,
    private val environment: SyncEnvironment,
    private val log: SyncRequestLog,
    private val settings: InvitationCheckSettings,
    private val clock: Clock
) : SourceSyncRequester {
    private val policy = SyncRequestPolicy(clock)

    override suspend fun requestSync(
        accounts: Set<CalendarAccount>,
        reason: SyncReason
    ): SyncRequests {
        val syncable = accounts.filterNot { it.isLocal }
        val interval = settings.intervals.first()
        val device = environment.device()
        val allowed = syncable.filter {
            val state = environment.account(it)
            policy.shouldRequest(reason, state, device, interval, log.lastRequest(it))
        }
        if (allowed.isEmpty()) return SyncRequests(0, 0, skipped = syncable.size)
        val result = delegate.requestSync(allowed.toSet(), reason)
        log.record(allowed, clock.instant())
        return result.copy(skipped = syncable.size - allowed.size)
    }
}
