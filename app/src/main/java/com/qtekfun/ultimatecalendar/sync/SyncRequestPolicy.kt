// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.sync.AccountSyncState
import com.qtekfun.ultimatecalendar.data.sync.DeviceSyncState
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Decides whether a check should ask an account to sync now (RF-06). Asking is the most
 * expensive thing a check does: it makes the Google or DAVx5 adapter open the network. The
 * rule:
 *
 * - [SyncReason.MANUAL] (pull to refresh): always, unless the provider says the account cannot
 *   sync at all.
 * - [SyncReason.WRITE] (the app wrote an invitation or an answer): not if the account cannot
 *   sync or has its calendar sync switched off, nor without a network; otherwise at once, with no
 *   wait and whatever the battery saver says, because the user's invitation is waiting.
 * - [SyncReason.BACKGROUND] (periodic job, app opening): not if the account cannot sync or has
 *   its calendar sync switched off, not in battery saver, not without a network, and not if the
 *   account was asked less than `max(interval, 30 min)` ago. The wait is shortened by
 *   [SLACK] because the periodic job drifts by a few minutes and would otherwise miss every
 *   other slot. What a sync brings still reaches the app through the provider's change
 *   notifications, which run a check without asking for a sync.
 */
class SyncRequestPolicy(private val clock: Clock) {
    fun shouldRequest(
        reason: SyncReason,
        account: AccountSyncState,
        device: DeviceSyncState,
        interval: CheckInterval,
        lastRequest: Instant?
    ): Boolean = when {
        !account.syncable -> false
        reason == SyncReason.MANUAL -> true
        reason == SyncReason.WRITE -> account.syncsEvents && device.networkAvailable
        !account.syncsEvents || device.batterySaver || !device.networkAvailable -> false
        else -> lastRequest == null || !withinWait(lastRequest, interval)
    }

    private fun withinWait(lastRequest: Instant, interval: CheckInterval): Boolean {
        val elapsed = Duration.between(lastRequest, clock.instant())
        // A negative time means the clock went back: the record cannot be trusted.
        return !elapsed.isNegative && elapsed < minimumWait(interval).minus(SLACK)
    }

    /** The shortest time between background requests for one account. */
    fun minimumWait(interval: CheckInterval): Duration =
        Duration.ofMinutes(maxOf(interval.minutes ?: 0L, MIN_WAIT_MINUTES))

    private companion object {
        const val MIN_WAIT_MINUTES = 30L
        val SLACK: Duration = Duration.ofMinutes(5)
    }
}
