// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.notify.MissedReminderRecovery

/**
 * The periodic invitation check (RF-06). It asks the accounts to sync first, then checks, and
 * afterwards recovers the reminders the system kept from showing (RF-08). Built by
 * [InvitationWorkerFactory].
 */
class InvitationCheckWorker(
    context: Context,
    params: WorkerParameters,
    private val checker: InvitationChecker,
    private val recovery: MissedReminderRecovery
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val outcome = checker.check(requestSync = true)
        recovery.recover()
        // Only a failing source is worth retrying soon; a revoked permission or a rejected
        // request will not mend itself, so the next period tries again.
        val sourceFailed = (outcome as? InvitationCheckOutcome.Failed)?.error is
            CalendarError.SourceFailure
        return if (sourceFailed) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "invitation-check"
    }
}
