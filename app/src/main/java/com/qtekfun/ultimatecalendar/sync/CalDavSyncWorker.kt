// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qtekfun.ultimatecalendar.sync.engine.SyncEngine
import com.qtekfun.ultimatecalendar.sync.engine.SyncOutcome

/** What a finished sync asks WorkManager to do next. */
enum class SyncVerdict { DONE, RETRY }

/**
 * One CalDAV sync (RF-12): queued changes up, server changes down. Started by WorkManager only
 * while an account is signed in, over a network connection (see `WorkManagerCalDavScheduler`);
 * without an account the engine does nothing and no request is made. Built by
 * [InvitationWorkerFactory].
 */
class CalDavSyncWorker(context: Context, params: WorkerParameters, private val engine: SyncEngine) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (verdict(engine.sync(), runAttemptCount)) {
        SyncVerdict.DONE -> Result.success()
        SyncVerdict.RETRY -> Result.retry()
    }

    companion object {
        const val PERIODIC_NAME = "caldav-sync"
        const val SOON_NAME = "caldav-sync-soon"
        const val PROMPT_NAME = "caldav-sync-prompt"
        const val NOW_NAME = "caldav-sync-now"

        /** After this many tries the next trigger (a change, the period) tries again instead. */
        const val MAX_ATTEMPTS = 5

        /**
         * A network problem, a server error and changes that could not be sent yet are worth
         * trying again with backoff, a few times. A refused login will not mend itself: the
         * account state tells the user, so the work just ends.
         */
        fun verdict(outcome: SyncOutcome, attempt: Int): SyncVerdict {
            val again = when (outcome) {
                is SyncOutcome.Ok -> outcome.pushed.retried > 0
                SyncOutcome.Offline, is SyncOutcome.Error -> true
                SyncOutcome.NoAccount, SyncOutcome.Unauthorized -> false
            }
            return if (again && attempt < MAX_ATTEMPTS) SyncVerdict.RETRY else SyncVerdict.DONE
        }
    }
}
