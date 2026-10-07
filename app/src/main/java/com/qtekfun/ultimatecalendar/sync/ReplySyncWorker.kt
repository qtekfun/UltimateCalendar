// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.sync.ReplyRetry
import com.qtekfun.ultimatecalendar.data.sync.SourceSyncRequester
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks the Android accounts to sync once more, urgently, after an answer to an invitation could
 * not be uploaded when it was given (offline, calendar sync off). It runs only with a connection,
 * backs off between tries and gives up after [MAX_ATTEMPTS], so a broken account cannot drain the
 * battery. Built by [InvitationWorkerFactory].
 */
class ReplySyncWorker(
    context: Context,
    params: WorkerParameters,
    private val source: CalendarSource,
    private val requester: SourceSyncRequester
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val calendars = (source.calendars() as? CalendarResult.Success)?.value
        val accounts = calendars?.map { it.account }?.filter { it.isSystemSynced() }?.toSet()
        val asked = accounts?.takeIf { it.isNotEmpty() }
            ?.let { requester.requestSync(it, SyncReason.MANUAL) }
        val refused = calendars == null ||
            (asked != null && (asked.failed > 0 || asked.requested == 0))
        return if (refused) retryOrGiveUp() else Result.success()
    }

    private fun retryOrGiveUp() =
        if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.success()

    private fun CalendarAccount.isSystemSynced() = !isLocal && !isCalDav && !isSubscription

    companion object {
        const val UNIQUE_NAME = "reply-sync"
        const val MAX_ATTEMPTS = 5
        const val BACKOFF_MINUTES = 5L
    }
}

/** [ReplyRetry] as one unique WorkManager job: asking again while one is pending changes nothing. */
@Singleton
class WorkManagerReplyRetry @Inject constructor(@ApplicationContext private val context: Context) :
    ReplyRetry {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<ReplySyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                ReplySyncWorker.BACKOFF_MINUTES,
                TimeUnit.MINUTES
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ReplySyncWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
