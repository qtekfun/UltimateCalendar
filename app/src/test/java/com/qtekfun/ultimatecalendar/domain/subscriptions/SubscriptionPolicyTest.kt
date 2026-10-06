// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.subscriptions

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionPolicyTest {
    private val now = Instant.parse("2026-10-06T12:00:00Z")

    private fun subscription(
        interval: RefreshInterval = RefreshInterval.EVERY_12_HOURS,
        enabled: Boolean = true,
        attemptedHoursAgo: Long? = null,
        error: SubscriptionError? = null,
        id: Long = 1
    ) = Subscription(
        id = id,
        name = "Holidays",
        color = 0,
        enabled = enabled,
        interval = interval,
        host = "example.com",
        lastAttemptAt = attemptedHoursAgo?.let { now.minusSeconds(it * 3600) },
        lastSuccessAt = null,
        error = error,
        errorCode = null,
        eventCount = 0,
        skipped = 0
    )

    @Test
    fun `one that was never tried is due, one tried less than its interval ago is not`() {
        assertTrue(SubscriptionPolicy.isDue(subscription(), now))
        assertFalse(SubscriptionPolicy.isDue(subscription(attemptedHoursAgo = 6), now))
        assertTrue(SubscriptionPolicy.isDue(subscription(attemptedHoursAgo = 12), now))
    }

    @Test
    fun `a run a little early still counts as the one that is due`() {
        val almost = subscription().copy(lastAttemptAt = now.minusSeconds(12 * 3600 - 20 * 60))
        val tooEarly = subscription().copy(lastAttemptAt = now.minusSeconds(12 * 3600 - 40 * 60))

        assertTrue(SubscriptionPolicy.isDue(almost, now))
        assertFalse(SubscriptionPolicy.isDue(tooEarly, now))
    }

    @Test
    fun `manual and disabled subscriptions are never due`() {
        assertFalse(SubscriptionPolicy.isDue(subscription(RefreshInterval.MANUAL), now))
        assertFalse(SubscriptionPolicy.isDue(subscription(enabled = false), now))
    }

    @Test
    fun `each interval is its own length`() {
        assertFalse(
            SubscriptionPolicy.isDue(
                subscription(RefreshInterval.EVERY_24_HOURS, attemptedHoursAgo = 12),
                now
            )
        )
        assertTrue(
            SubscriptionPolicy.isDue(
                subscription(RefreshInterval.EVERY_6_HOURS, attemptedHoursAgo = 7),
                now
            )
        )
    }

    @Test
    fun `a retry also takes what failed, a normal run only what is due`() {
        val failed = subscription(attemptedHoursAgo = 1, error = SubscriptionError.TIMEOUT, id = 1)
        val fine = subscription(attemptedHoursAgo = 1, id = 2)
        val due = subscription(attemptedHoursAgo = 13, id = 3)
        val manualFailed =
            subscription(RefreshInterval.MANUAL, error = SubscriptionError.TLS, id = 4)

        assertEquals(
            listOf(3L),
            SubscriptionPolicy.toRefresh(listOf(failed, fine, due), now, retrying = false)
                .map { it.id }
        )
        assertEquals(
            listOf(1L, 3L),
            SubscriptionPolicy.toRefresh(
                listOf(failed, fine, due, manualFailed),
                now,
                retrying = true
            ).map { it.id }
        )
    }

    @Test
    fun `the plan is the shortest interval of the subscriptions that refresh by themselves`() {
        assertEquals(SchedulePlan.None, SubscriptionPolicy.plan(emptyList()))
        assertEquals(
            SchedulePlan.None,
            SubscriptionPolicy.plan(
                listOf(
                    subscription(RefreshInterval.MANUAL),
                    subscription(RefreshInterval.EVERY_6_HOURS, enabled = false)
                )
            )
        )
        assertEquals(
            SchedulePlan.Every(12),
            SubscriptionPolicy.plan(
                listOf(
                    subscription(RefreshInterval.EVERY_24_HOURS),
                    subscription(RefreshInterval.EVERY_12_HOURS),
                    subscription(RefreshInterval.MANUAL),
                    subscription(RefreshInterval.EVERY_6_HOURS, enabled = false)
                )
            )
        )
    }

    @Test
    fun `only network and server trouble is worth another try soon`() {
        listOf(
            SubscriptionError.TIMEOUT,
            SubscriptionError.UNREACHABLE,
            SubscriptionError.NETWORK
        ).forEach { assertTrue(SubscriptionPolicy.isTemporary(it, null)) }
        listOf(
            SubscriptionError.TLS,
            SubscriptionError.TOO_LARGE,
            SubscriptionError.INSECURE_REDIRECT,
            SubscriptionError.TOO_MANY_REDIRECTS,
            SubscriptionError.NOT_CALENDAR,
            SubscriptionError.UNREADABLE_URL
        ).forEach { assertFalse(SubscriptionPolicy.isTemporary(it, null)) }

        assertTrue(SubscriptionPolicy.isTemporary(SubscriptionError.HTTP, 503))
        assertTrue(SubscriptionPolicy.isTemporary(SubscriptionError.HTTP, 429))
        assertTrue(SubscriptionPolicy.isTemporary(SubscriptionError.HTTP, null))
        assertFalse(SubscriptionPolicy.isTemporary(SubscriptionError.HTTP, 404))
    }

    @Test
    fun `names are trimmed, limited and fall back to the host`() {
        assertEquals("Holidays", SubscriptionPolicy.cleanName("  Holidays  ", "example.com"))
        assertEquals("example.com", SubscriptionPolicy.cleanName("   ", "example.com"))
        assertEquals(
            SubscriptionPolicy.MAX_NAME_LENGTH,
            SubscriptionPolicy.cleanName("x".repeat(500), "h").length
        )
        assertEquals("a", SubscriptionPolicy.cleanName("a" + " ".repeat(200) + "b", "h"))
    }

    @Test
    fun `intervals are found by their hours`() {
        assertEquals(RefreshInterval.EVERY_6_HOURS, RefreshInterval.ofHours(6))
        assertEquals(RefreshInterval.MANUAL, RefreshInterval.ofHours(0))
        assertNull(RefreshInterval.ofHours(7))
        assertEquals(RefreshInterval.EVERY_12_HOURS, RefreshInterval.DEFAULT)
    }

    @Test
    fun `a subscription is failing when it has an error`() {
        assertTrue(subscription(error = SubscriptionError.TLS).failing)
        assertFalse(subscription().failing)
    }
}
