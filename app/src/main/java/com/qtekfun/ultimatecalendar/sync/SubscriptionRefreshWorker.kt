// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qtekfun.ultimatecalendar.data.subscriptions.RefreshSummary
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionRefresher

/**
 * One refresh of the subscriptions (T39): the ones that are due, or all the enabled ones when the
 * user asked ([ALL_KEY]). Started by WorkManager only while there is a subscription that
 * refreshes by itself, and only with a connection (see `WorkManagerSubscriptionScheduler`). A feed
 * that has not changed costs one small conditional request. Built by [InvitationWorkerFactory].
 */
class SubscriptionRefreshWorker(
    context: Context,
    params: WorkerParameters,
    private val refresher: SubscriptionRefresher
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val summary = if (inputData.getBoolean(ALL_KEY, false)) {
            refresher.refreshAll()
        } else {
            refresher.refreshDue(retrying = runAttemptCount > 0)
        }
        return when (verdict(summary, runAttemptCount)) {
            SyncVerdict.DONE -> Result.success()
            SyncVerdict.RETRY -> Result.retry()
        }
    }

    companion object {
        const val PERIODIC_NAME = "subscriptions-refresh"
        const val NOW_NAME = "subscriptions-refresh-now"
        const val ALL_KEY = "all"

        /** After this many tries the next period (or the user) tries again instead. */
        const val MAX_ATTEMPTS = 4

        /**
         * A network or server problem is tried again with backoff, a few times; a feed that is not
         * a calendar, an address that went insecure or a refused request will not mend itself,
         * and its error is shown for the user to see.
         */
        fun verdict(summary: RefreshSummary, attempt: Int): SyncVerdict =
            if (summary.temporaryFailures > 0 && attempt < MAX_ATTEMPTS) {
                SyncVerdict.RETRY
            } else {
                SyncVerdict.DONE
            }
    }
}
