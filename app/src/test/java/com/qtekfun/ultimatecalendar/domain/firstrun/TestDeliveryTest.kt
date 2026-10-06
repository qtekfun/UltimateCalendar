// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TestDeliveryTest {
    private val at = Instant.parse("2026-10-05T10:01:00Z")

    @Test
    fun `nothing to say before the first test`() {
        assertNull(TestDelivery.of(null, null, at))
    }

    @Test
    fun `waits up to ten minutes, then it is missing`() {
        assertEquals(TestDelivery.Waiting(at), TestDelivery.of(at, null, at.minusSeconds(30)))
        assertEquals(TestDelivery.Waiting(at), TestDelivery.of(at, null, at.plusSeconds(600)))
        assertEquals(TestDelivery.Missing(at), TestDelivery.of(at, null, at.plusSeconds(601)))
    }

    @Test
    fun `within a minute is on time, later says by how much`() {
        assertEquals(
            TestDelivery.OnTime(at, at.plusSeconds(60)),
            TestDelivery.of(at, at.plusSeconds(60), at)
        )
        assertEquals(
            TestDelivery.OnTime(at, at.minusSeconds(1)),
            TestDelivery.of(at, at.minusSeconds(1), at)
        )
        assertEquals(
            TestDelivery.Late(at, at.plusSeconds(61), 1),
            TestDelivery.of(at, at.plusSeconds(61), at)
        )
        assertEquals(
            TestDelivery.Late(at, at.plusSeconds(7 * 60 + 5), 7),
            TestDelivery.of(at, at.plusSeconds(7 * 60 + 5), at.plusSeconds(3600))
        )
    }
}
