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
import com.qtekfun.ultimatecalendar.data.sync.PendingUploads
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
 * Follows the answers to invitations until their accounts have uploaded them (RF-07). Each run
 * reads which Android accounts still hold a change to upload ([PendingUploads]) and asks those to
 * sync, urgently; when none does, the replies have left the phone and it is done. Otherwise it
 * runs again, backing off, and gives up after [MAX_ATTEMPTS], so an account that never uploads
 * cannot drain the battery: the periodic check keeps asking at its own pace. It runs only with a
 * connection. When the provider cannot tell what is pending, every Android account is asked once
 * and the run is done when they were. Built by [InvitationWorkerFactory].
 */
class ReplySyncWorker(
    context: Context,
    params: WorkerParameters,
    private val source: CalendarSource,
    private val uploads: PendingUploads,
    private val requester: SourceSyncRequester
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val pending = uploads.accounts()
        return if (pending == null) askEveryAccount() else askPending(pending)
    }

    private suspend fun askPending(pending: Set<CalendarAccount>): Result {
        val waiting = pending.filter { it.isSystemSynced() }.toSet()
        if (waiting.isEmpty()) return Result.success()
        requester.requestSync(waiting, SyncReason.MANUAL)
        return retryOrGiveUp()
    }

    private suspend fun askEveryAccount(): Result {
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
        const val MAX_ATTEMPTS = 6

        /** The first look, once the request made with the answer has had time to work. */
        const val FIRST_CHECK_SECONDS = 45L

        /** Doubles each time: the tries span about a quarter of an hour. */
        const val BACKOFF_SECONDS = 30L
    }
}

/**
 * [ReplyRetry] as one unique WorkManager job. A new answer starts the follow-up again, with all
 * its tries, in place of the one under way: one job covers every pending answer.
 */
@Singleton
class WorkManagerReplyRetry @Inject constructor(@ApplicationContext private val context: Context) :
    ReplyRetry {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<ReplySyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setInitialDelay(ReplySyncWorker.FIRST_CHECK_SECONDS, TimeUnit.SECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                ReplySyncWorker.BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ReplySyncWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
