// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import javax.inject.Inject

/**
 * Asks, later and when the phone has a connection, for another sync of the accounts that have an
 * answer to an invitation waiting to be uploaded. The request must survive the process dying and
 * must be bounded (it gives up after a few tries).
 */
fun interface ReplyRetry {
    fun schedule()
}

/**
 * Whether an answer to an invitation can reach the organizer now (RF-07). The answer itself is
 * written to the provider, which marks the event for upload; Google or DAVx5 upload it when they
 * sync. When the account cannot sync at this moment (no network, the account's calendar sync is
 * off, the provider says it cannot sync) the answer waits, and [retryIfWaiting] schedules another
 * try for when conditions allow. Battery saver does not count: the expedited request the app
 * makes after an answer is a manual one, which it does not hold back.
 */
class ReplyDelivery @Inject constructor(
    private val environment: SyncEnvironment,
    private val retry: ReplyRetry
) {
    /** True when the answer written to a calendar of [account] cannot be uploaded right now. */
    fun isWaiting(account: CalendarAccount): Boolean = when {
        account.isLocal || account.isSubscription -> false

        account.isCalDav -> !environment.device().networkAvailable

        else -> {
            val state = environment.account(account)
            !state.syncable || !state.syncsEvents || !environment.device().networkAvailable
        }
    }

    /** Schedules the retry when an answer to a calendar of [account] is waiting; true then. */
    fun retryIfWaiting(account: CalendarAccount): Boolean {
        val waiting = isWaiting(account)
        // The app's own CalDAV queue sends by itself when the connection is back.
        if (waiting && !account.isCalDav) retry.schedule()
        return waiting
    }
}
