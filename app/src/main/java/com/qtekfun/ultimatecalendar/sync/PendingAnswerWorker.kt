// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qtekfun.ultimatecalendar.data.invitations.AnswerAttempt
import com.qtekfun.ultimatecalendar.data.invitations.ForeignAnswers
import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.invitations.PendingAnswerRetry
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.invitations.NotificationTags
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.notify.InvitationNotificationSurface
import com.qtekfun.ultimatecalendar.notify.InvitationRecheck
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gives the answer to an invitation for another of the user's accounts once that account has
 * received the event (RF-07). Each try asks the account to sync urgently and looks for its copy;
 * when it is there the answer is written to it, the notification goes and a check brings the
 * records up to date. It runs only with a connection, backs off between tries and, after
 * [MAX_ATTEMPTS], says honestly that the copy never arrived instead of pretending. The work
 * survives the process dying: the key and the answer are its input. Built by
 * [InvitationWorkerFactory].
 */
class PendingAnswerWorker(
    context: Context,
    private val params: WorkerParameters,
    private val answers: ForeignAnswers,
    private val surface: InvitationNotificationSurface,
    private val notified: NotifiedInvitations,
    private val recheck: InvitationRecheck
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val key = keyOf(params.inputData)
        val status = AttendeeStatus.entries
            .firstOrNull { it.name == params.inputData.getString(STATUS) }
        if (key == null || status == null) return Result.failure()
        val last = params.runAttemptCount + 1 >= MAX_ATTEMPTS
        return when (answers.attempt(key, status, last)) {
            AnswerAttempt.ANSWERED, AnswerAttempt.GONE -> {
                surface.cancel(key)
                surface.refreshSummary()
                recheck.run()
                Result.success()
            }

            AnswerAttempt.RETRY -> Result.retry()

            AnswerAttempt.GAVE_UP -> {
                notified.load().firstOrNull { it.key == key }?.let(surface::showNeverArrived)
                Result.success()
            }
        }
    }

    private fun keyOf(data: Data): InvitationKey? {
        val calendar = data.getLong(CALENDAR, MISSING)
        val event = data.getLong(EVENT, MISSING)
        val address = data.getString(ADDRESS)
        return if (calendar == MISSING || event == MISSING || address.isNullOrEmpty()) {
            null
        } else {
            InvitationKey(CalendarId(calendar), EventId(event), address)
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 6
        const val BACKOFF_MINUTES = 2L
        private const val CALENDAR = "calendar"
        private const val EVENT = "event"
        private const val ADDRESS = "address"
        private const val STATUS = "status"
        private const val MISSING = -1L

        /** The unique name of the work for [key]: asking again replaces the pending answer. */
        fun uniqueName(key: InvitationKey) = "pending-answer/" + NotificationTags.id(key)

        fun inputOf(key: InvitationKey, status: AttendeeStatus): Data = Data.Builder()
            .putLong(CALENDAR, key.calendarId.value)
            .putLong(EVENT, key.eventId.value)
            .putString(ADDRESS, key.address)
            .putString(STATUS, status.name)
            .build()
    }
}

/** [PendingAnswerRetry] as one unique WorkManager job per invitation. */
@Singleton
class WorkManagerPendingAnswers @Inject constructor(
    @ApplicationContext private val context: Context
) : PendingAnswerRetry {
    override fun schedule(key: InvitationKey, status: AttendeeStatus) {
        val request = OneTimeWorkRequestBuilder<PendingAnswerWorker>()
            .setInputData(PendingAnswerWorker.inputOf(key, status))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                PendingAnswerWorker.BACKOFF_MINUTES,
                TimeUnit.MINUTES
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PendingAnswerWorker.uniqueName(key),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
