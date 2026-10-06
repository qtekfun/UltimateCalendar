// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionScheduler
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.sync.CalDavSyncScheduler
import com.qtekfun.ultimatecalendar.sync.ThrottledSyncRequester
import javax.inject.Inject

/**
 * Asks every kind of account to sync: Android accounts through the system (limited as
 * [ThrottledSyncRequester] decides), the app's own CalDAV account through its sync work and the
 * subscriptions through their refresh work. Those sync by themselves, periodically (the CalDAV
 * account also after each local change), so only the user's request ([SyncReason.MANUAL]) starts
 * one here: the periodic check must not add to it. Subscriptions are not Android accounts and are
 * never passed to the system. With neither, the answer is the throttled provider requester's,
 * unchanged.
 */
class CompositeSyncRequester @Inject constructor(
    private val provider: ThrottledSyncRequester,
    private val caldav: CalDavSyncScheduler,
    private val subscriptions: SubscriptionScheduler
) : SourceSyncRequester {
    override suspend fun requestSync(
        accounts: Set<CalendarAccount>,
        reason: SyncReason
    ): SyncRequests {
        val (own, others) = accounts.partition { it.isCalDav }
        val (feeds, android) = others.partition { it.isSubscription }
        val requests = provider.requestSync(android.toSet(), reason)
        if (reason != SyncReason.MANUAL) return requests
        var started = 0
        if (own.isNotEmpty()) {
            caldav.syncNow()
            started++
        }
        if (feeds.isNotEmpty()) {
            subscriptions.refreshNow()
            started++
        }
        return requests.copy(requested = requests.requested + started)
    }
}
