// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Requests a sync for each account of the Android calendar provider, one by one: a refusal for
 * one account (the system, a missing adapter) does not stop the others. Accounts without a sync
 * adapter, the on-device "LOCAL" account, are skipped.
 *
 * Whether this really makes Google and DAVx5 sync is not verified yet (T02, SPEC §8).
 */
class ProviderSyncRequester @Inject constructor(
    private val trigger: AccountSyncTrigger,
    @IoDispatcher private val io: CoroutineDispatcher
) : SourceSyncRequester {
    override suspend fun requestSync(accounts: Set<CalendarAccount>): SyncRequests =
        withContext(io) {
            var requested = 0
            var failed = 0
            for (account in accounts.filter { it.type != LOCAL_ACCOUNT_TYPE }) {
                try {
                    trigger.request(account)
                    requested++
                } catch (_: SecurityException) {
                    failed++
                } catch (_: IllegalArgumentException) {
                    failed++
                }
            }
            SyncRequests(requested, failed)
        }

    private companion object {
        /** `CalendarContract.ACCOUNT_TYPE_LOCAL`. */
        const val LOCAL_ACCOUNT_TYPE = "LOCAL"
    }
}
