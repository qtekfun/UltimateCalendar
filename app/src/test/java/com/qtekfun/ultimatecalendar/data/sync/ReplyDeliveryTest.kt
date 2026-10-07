// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReplyDeliveryTest {
    private var account = AccountSyncState(syncable = true, syncsEvents = true)
    private var device = DeviceSyncState(batterySaver = false, networkAvailable = true)
    private var retries = 0
    private val delivery = ReplyDelivery(
        object : SyncEnvironment {
            override fun account(account: CalendarAccount) = this@ReplyDeliveryTest.account

            override fun device() = device
        },
        { retries++ }
    )
    private val google = CalendarAccount("me@gmail.com", "com.google")

    @Test
    fun `an account that can sync now is not waiting and schedules nothing`() {
        assertFalse(delivery.retryIfWaiting(google))
        assertEquals(0, retries)
    }

    @Test
    fun `battery saver does not hold an answer back`() {
        device = DeviceSyncState(batterySaver = true, networkAvailable = true)
        assertFalse(delivery.isWaiting(google))
    }

    @Test
    fun `no network, calendar sync off or an account that cannot sync make it wait and retry`() {
        device = DeviceSyncState(batterySaver = false, networkAvailable = false)
        assertTrue(delivery.retryIfWaiting(google))
        device = DeviceSyncState(batterySaver = false, networkAvailable = true)
        account = AccountSyncState(syncable = true, syncsEvents = false)
        assertTrue(delivery.retryIfWaiting(google))
        account = AccountSyncState(syncable = false, syncsEvents = true)
        assertTrue(delivery.retryIfWaiting(google))
        assertEquals(3, retries)
    }

    @Test
    fun `local and subscription calendars never wait`() {
        device = DeviceSyncState(batterySaver = false, networkAvailable = false)
        assertFalse(delivery.retryIfWaiting(CalendarAccount("x", "LOCAL")))
        assertFalse(
            delivery.retryIfWaiting(CalendarAccount("x", CalendarAccount.SUBSCRIPTION_TYPE))
        )
        assertEquals(0, retries)
    }

    @Test
    fun `a caldav answer waits offline but leaves the retry to its own queue`() {
        val caldav = CalendarAccount("me", CalendarAccount.CALDAV_TYPE)
        assertFalse(delivery.retryIfWaiting(caldav))
        device = DeviceSyncState(batterySaver = false, networkAvailable = false)
        assertTrue(delivery.retryIfWaiting(caldav))
        assertEquals(0, retries)
    }
}
