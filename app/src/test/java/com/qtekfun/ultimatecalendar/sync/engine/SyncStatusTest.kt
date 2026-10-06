// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import com.qtekfun.ultimatecalendar.sync.engine.SyncStatus.Phase
import com.qtekfun.ultimatecalendar.sync.queue.ProcessResult
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncStatusTest {
    private val earlier = Instant.parse("2026-10-05T08:00:00Z")

    @Test
    fun `a sync in progress wins over whatever happened before`() {
        val status = SyncStatus.of(SyncOutcome.Offline, syncing = true, lastOk = earlier)

        assertEquals(SyncStatus(Phase.SYNCING, earlier), status)
        assertFalse(status.canRetry)
    }

    @Test
    fun `with no outcome yet it depends on whether a sync ever worked`() {
        assertEquals(Phase.NEVER, SyncStatus.of(null, false, null).phase)
        assertEquals(Phase.OK, SyncStatus.of(null, false, earlier).phase)
    }

    @Test
    fun `each outcome has its own phase and keeps the last good time`() {
        fun phase(outcome: SyncOutcome) = SyncStatus.of(outcome, false, earlier)

        assertEquals(SyncStatus(Phase.OK, earlier), phase(SyncOutcome.Ok(ProcessResult())))
        assertEquals(Phase.OFFLINE, phase(SyncOutcome.Offline).phase)
        assertEquals(Phase.REFUSED, phase(SyncOutcome.Unauthorized).phase)
        assertEquals(Phase.FAILED, phase(SyncOutcome.Error("HTTP 500")).phase)
        // Only a signed-in account has anything to say: no account means nothing synced.
        assertEquals(Phase.OK, phase(SyncOutcome.NoAccount).phase)
        assertEquals(Phase.NEVER, SyncStatus.of(SyncOutcome.NoAccount, false, null).phase)
    }

    @Test
    fun `everything but a running sync can be tried again`() {
        Phase.entries.filter { it != Phase.SYNCING }.forEach {
            assertTrue(SyncStatus(it, null).canRetry, it.name)
        }
    }
}
