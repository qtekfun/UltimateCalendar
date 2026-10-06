// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.sync.CalDavSyncScheduler
import javax.inject.Inject

/**
 * Asks every kind of account to sync: Android accounts through the system, the app's own CalDAV
 * account through its sync work. With no CalDAV account it is the provider requester, unchanged.
 */
class CompositeSyncRequester @Inject constructor(
    private val provider: ProviderSyncRequester,
    private val caldav: CalDavSyncScheduler
) : SourceSyncRequester {
    override suspend fun requestSync(accounts: Set<CalendarAccount>): SyncRequests {
        val (own, android) = accounts.partition { it.isCalDav }
        val requests = provider.requestSync(android.toSet())
        if (own.isEmpty()) return requests
        caldav.syncNow()
        return requests.copy(requested = requests.requested + 1)
    }
}
