// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.sync.CalDavSyncScheduler
import com.qtekfun.ultimatecalendar.sync.ThrottledSyncRequester
import javax.inject.Inject

/**
 * Asks every kind of account to sync: Android accounts through the system (limited as
 * [ThrottledSyncRequester] decides), the app's own CalDAV account through its sync work. The
 * CalDAV account syncs by itself, periodically and after each local change, so only the user's
 * request ([SyncReason.MANUAL]) starts a sync here: the periodic check must not add to it. With
 * no CalDAV account the answer is the throttled provider requester's, unchanged.
 */
class CompositeSyncRequester @Inject constructor(
    private val provider: ThrottledSyncRequester,
    private val caldav: CalDavSyncScheduler
) : SourceSyncRequester {
    override suspend fun requestSync(
        accounts: Set<CalendarAccount>,
        reason: SyncReason
    ): SyncRequests {
        val (own, android) = accounts.partition { it.isCalDav }
        val requests = provider.requestSync(android.toSet(), reason)
        if (own.isEmpty() || reason != SyncReason.MANUAL) return requests
        caldav.syncNow()
        return requests.copy(requested = requests.requested + 1)
    }
}
