// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import java.time.Instant

/**
 * What the account screen says about the sync (RF-12): how the last one went and when the last
 * good one finished. [lastOk] is kept even when the latest sync failed, so the screen can say
 * "failed, last synced yesterday".
 */
data class SyncStatus(val phase: Phase, val lastOk: Instant?) {
    enum class Phase {
        /** A sync is running now. */
        SYNCING,

        /** The latest sync worked (or none ran in this process, but one worked before). */
        OK,

        /** Nothing has ever synced. */
        NEVER,

        /** The server could not be reached; the next sync tries again. */
        OFFLINE,

        /** The server no longer accepts the app password: signing in again is the way out. */
        REFUSED,

        /** The server answered something the sync could not use. */
        FAILED
    }

    /** The user can fix it by trying again ("Sync now" is the retry). */
    val canRetry: Boolean get() = phase != Phase.SYNCING

    companion object {
        fun of(outcome: SyncOutcome?, syncing: Boolean, lastOk: Instant?): SyncStatus {
            val phase = when {
                syncing -> Phase.SYNCING
                outcome == null -> if (lastOk == null) Phase.NEVER else Phase.OK
                else -> phaseOf(outcome, lastOk)
            }
            return SyncStatus(phase, lastOk)
        }

        private fun phaseOf(outcome: SyncOutcome, lastOk: Instant?): Phase = when (outcome) {
            is SyncOutcome.Ok -> Phase.OK
            SyncOutcome.NoAccount -> if (lastOk == null) Phase.NEVER else Phase.OK
            SyncOutcome.Offline -> Phase.OFFLINE
            SyncOutcome.Unauthorized -> Phase.REFUSED
            is SyncOutcome.Error -> Phase.FAILED
        }
    }
}
