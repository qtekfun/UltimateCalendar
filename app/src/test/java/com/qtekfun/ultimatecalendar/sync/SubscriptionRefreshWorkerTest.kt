// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.subscriptions.RefreshSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionRefreshWorkerTest {
    private fun verdict(failures: Int, attempt: Int) =
        SubscriptionRefreshWorker.verdict(RefreshSummary(2, failures), attempt)

    @Test
    fun `a network or server problem is tried again with backoff, a few times`() {
        (0 until SubscriptionRefreshWorker.MAX_ATTEMPTS).forEach {
            assertEquals(SyncVerdict.RETRY, verdict(1, it), "attempt $it")
        }
        assertEquals(SyncVerdict.DONE, verdict(1, SubscriptionRefreshWorker.MAX_ATTEMPTS))
    }

    @Test
    fun `success, or only problems that will not mend themselves, end the work`() {
        assertEquals(SyncVerdict.DONE, verdict(0, 0))
        assertEquals(SyncVerdict.DONE, SubscriptionRefreshWorker.verdict(RefreshSummary(0, 0), 3))
    }
}
