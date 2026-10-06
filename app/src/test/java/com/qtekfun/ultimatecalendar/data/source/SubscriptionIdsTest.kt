// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionIdsTest {
    @Test
    fun `a subscription row survives encode and decode`() {
        listOf(1L, 2L, 99L, 1L shl 40, (1L shl 61) - 1).forEach { row ->
            val id = SubscriptionIds.encode(row)

            assertTrue(SubscriptionIds.isSubscription(id))
            assertEquals(row, SubscriptionIds.decode(id))
            assertEquals(row, SubscriptionIds.rowOf(SubscriptionIds.calendar(row)))
            assertEquals(row, SubscriptionIds.rowOf(SubscriptionIds.event(row)))
        }
    }

    @Test
    fun `provider ids and the test reminder are not subscriptions`() {
        listOf(1L, 42L, 1_000_000L, 0L, -1L, Long.MIN_VALUE).forEach {
            assertFalse(SubscriptionIds.isSubscription(it), "$it")
            assertEquals(SourceKind.PROVIDER, SourceKind.of(it), "$it")
        }
        assertFalse(SubscriptionIds.isSubscription(CalendarId(7)))
        assertFalse(SubscriptionIds.isSubscription(EventId(7)))
    }

    @Test
    fun `a subscription id is never a CalDAV id and the other way round`() {
        listOf(1L, 2L, 12_345L, (1L shl 61) - 1).forEach { row ->
            val feed = SubscriptionIds.encode(row)
            val dav = CalDavIds.encode(row)

            assertFalse(CalDavIds.isCalDav(feed), "feed $row")
            assertFalse(SubscriptionIds.isSubscription(dav), "dav $row")
            assertTrue(feed != dav)
            assertEquals(SourceKind.SUBSCRIPTION, SourceKind.of(feed))
            assertEquals(SourceKind.CALDAV, SourceKind.of(dav))
        }
    }

    @Test
    fun `a CalDAV row so large that it has bit 61 is still a CalDAV id`() {
        val dav = CalDavIds.encode(1L shl 61)

        assertEquals(SourceKind.CALDAV, SourceKind.of(dav))
        assertFalse(SubscriptionIds.isSubscription(dav))
    }

    @Test
    fun `existing provider and CalDAV ids keep their meaning`() {
        assertEquals(1L or (1L shl 62), CalDavIds.encode(1))
        assertEquals(SourceKind.PROVIDER, SourceKind.of(CalendarId(3)))
        assertEquals(SourceKind.CALDAV, SourceKind.of(CalDavIds.calendar(3)))
        assertEquals(SourceKind.SUBSCRIPTION, SourceKind.of(SubscriptionIds.calendar(3)))
        assertEquals(SourceKind.SUBSCRIPTION, SourceKind.of(SubscriptionIds.event(3)))
    }

    @Test
    fun `values that are not rows or not subscriptions are refused`() {
        assertThrows(IllegalArgumentException::class.java) { SubscriptionIds.encode(0) }
        assertThrows(IllegalArgumentException::class.java) { SubscriptionIds.encode(-5) }
        assertThrows(IllegalArgumentException::class.java) { SubscriptionIds.encode(1L shl 61) }
        assertThrows(IllegalArgumentException::class.java) { SubscriptionIds.decode(5) }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionIds.decode(CalDavIds.encode(5))
        }
    }
}
