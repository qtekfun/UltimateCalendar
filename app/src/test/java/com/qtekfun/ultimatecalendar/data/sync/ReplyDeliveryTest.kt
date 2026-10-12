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
    fun `an answer to an account that can sync now is followed and is not waiting`() {
        assertFalse(delivery.follow(google))
        assertEquals(1, retries)
    }

    @Test
    fun `battery saver does not hold an answer back`() {
        device = DeviceSyncState(batterySaver = true, networkAvailable = true)
        assertFalse(delivery.isWaiting(google))
    }

    @Test
    fun `no network, calendar sync off or an account that cannot sync make it wait`() {
        device = DeviceSyncState(batterySaver = false, networkAvailable = false)
        assertTrue(delivery.follow(google))
        device = DeviceSyncState(batterySaver = false, networkAvailable = true)
        account = AccountSyncState(syncable = true, syncsEvents = false)
        assertTrue(delivery.follow(google))
        account = AccountSyncState(syncable = false, syncsEvents = true)
        assertTrue(delivery.follow(google))
        assertEquals(3, retries)
    }

    @Test
    fun `local and subscription calendars never wait and are not followed`() {
        device = DeviceSyncState(batterySaver = false, networkAvailable = false)
        assertFalse(delivery.follow(CalendarAccount("x", "LOCAL")))
        assertFalse(delivery.follow(CalendarAccount("x", CalendarAccount.SUBSCRIPTION_TYPE)))
        assertEquals(0, retries)
    }

    @Test
    fun `a caldav answer waits offline but leaves the follow-up to its own queue`() {
        val caldav = CalendarAccount("me", CalendarAccount.CALDAV_TYPE)
        assertFalse(delivery.follow(caldav))
        device = DeviceSyncState(batterySaver = false, networkAvailable = false)
        assertTrue(delivery.follow(caldav))
        assertEquals(0, retries)
    }
}
