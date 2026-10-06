// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.sync.AccountSyncState
import com.qtekfun.ultimatecalendar.data.sync.DeviceSyncState
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.now
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncRequestPolicyTest {
    private val clock = MutableClock(now)
    private val policy = SyncRequestPolicy(clock)
    private val ready = AccountSyncState(syncable = true, syncsEvents = true)
    private val normal = DeviceSyncState(batterySaver = false, networkAvailable = true)

    private fun ago(minutes: Long): Instant = now.minus(Duration.ofMinutes(minutes))

    private fun background(
        account: AccountSyncState = ready,
        device: DeviceSyncState = normal,
        interval: CheckInterval = CheckInterval.QUARTER_HOUR,
        last: Instant? = null
    ) = policy.shouldRequest(SyncReason.BACKGROUND, account, device, interval, last)

    private fun manual(
        account: AccountSyncState = ready,
        device: DeviceSyncState = normal,
        last: Instant? = null
    ) = policy.shouldRequest(SyncReason.MANUAL, account, device, CheckInterval.HOUR, last)

    @Test
    fun `the first background run asks`() {
        assertTrue(background(last = null))
    }

    @Test
    fun `a background run asks only once the wait, less the drift, has passed`() {
        // 15 min interval: the wait is 30 min, shortened by 5 min of drift.
        assertFalse(background(last = ago(0)))
        assertFalse(background(last = ago(15)))
        assertFalse(background(last = ago(24)))
        assertTrue(background(last = ago(25)))
        assertTrue(background(last = ago(30)))
    }

    @Test
    fun `with the 15 minute job every other run asks`() {
        assertFalse(background(last = ago(15)))
        assertTrue(background(last = ago(30)))
    }

    @Test
    fun `the wait follows the interval when it is longer than 30 minutes`() {
        assertFalse(background(interval = CheckInterval.HOUR, last = ago(54)))
        assertTrue(background(interval = CheckInterval.HOUR, last = ago(55)))
        assertFalse(background(interval = CheckInterval.HALF_HOUR, last = ago(24)))
        assertTrue(background(interval = CheckInterval.HALF_HOUR, last = ago(25)))
    }

    @Test
    fun `without a periodic job the wait is still 30 minutes`() {
        assertEquals(Duration.ofMinutes(30), policy.minimumWait(CheckInterval.MANUAL_ONLY))
        assertEquals(Duration.ofMinutes(30), policy.minimumWait(CheckInterval.QUARTER_HOUR))
        assertEquals(Duration.ofMinutes(60), policy.minimumWait(CheckInterval.HOUR))
        assertFalse(background(interval = CheckInterval.MANUAL_ONLY, last = ago(10)))
        assertTrue(background(interval = CheckInterval.MANUAL_ONLY, last = ago(26)))
    }

    @Test
    fun `a record from the future, with the clock set back, does not block`() {
        assertTrue(background(last = now.plus(Duration.ofMinutes(10))))
    }

    @Test
    fun `a background run does not ask an account that cannot sync or has sync off`() {
        assertFalse(background(account = AccountSyncState(syncable = false, syncsEvents = true)))
        assertFalse(background(account = AccountSyncState(syncable = true, syncsEvents = false)))
    }

    @Test
    fun `a background run does not ask in battery saver or without a network`() {
        assertFalse(background(device = normal.copy(batterySaver = true)))
        assertFalse(background(device = normal.copy(networkAvailable = false)))
    }

    @Test
    fun `a manual refresh always asks, whatever the last request or the device`() {
        assertTrue(manual(last = ago(0)))
        assertTrue(manual(last = now.plus(Duration.ofMinutes(10))))
        assertTrue(manual(device = DeviceSyncState(batterySaver = true, networkAvailable = false)))
        assertTrue(manual(account = AccountSyncState(syncable = true, syncsEvents = false)))
    }

    @Test
    fun `a manual refresh still skips an account that cannot sync at all`() {
        assertFalse(manual(account = AccountSyncState(syncable = false, syncsEvents = true)))
    }
}
