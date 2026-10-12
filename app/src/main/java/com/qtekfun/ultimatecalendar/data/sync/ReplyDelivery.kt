// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import javax.inject.Inject

/**
 * Checks, a little later and while the phone has a connection, that the answers to invitations
 * have really been uploaded, and asks the accounts that still hold one to sync again. It must
 * survive the process dying and be bounded (it gives up after a few tries).
 */
fun interface ReplyRetry {
    fun schedule()
}

/**
 * Whether an answer to an invitation can reach the organizer now (RF-07). The answer itself is
 * written to the provider, which marks the event for upload; Google or DAVx5 upload it when they
 * sync. The app asks for that sync at once, but a request is no promise: the system may hold it
 * back, or the account cannot sync at this moment (no network, the account's calendar sync is
 * off, the provider says it cannot sync). So every answer is followed ([follow]) until it is
 * uploaded. Battery saver does not count: the request the app makes after an answer is a manual
 * one, which it does not hold back.
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

    /**
     * Follows an answer just written to a calendar of [account] until it is uploaded, and tells
     * whether it has to wait for the account to be able to sync. The app's own CalDAV queue
     * sends by itself when the connection is back; local and subscription calendars send nothing.
     */
    fun follow(account: CalendarAccount): Boolean {
        if (!account.isLocal && !account.isSubscription && !account.isCalDav) retry.schedule()
        return isWaiting(account)
    }
}
