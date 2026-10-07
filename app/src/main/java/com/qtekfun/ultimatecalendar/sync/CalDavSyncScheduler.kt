// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavSyncTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When the CalDAV account syncs: periodically, soon after a local change and on demand. Nothing
 * is scheduled without an account (see [CalDavSync]).
 */
interface CalDavSyncScheduler {
    /** Keeps one periodic sync going; calling it again changes nothing. */
    fun schedulePeriodic()

    /** Stops every scheduled sync: there is no account any more. */
    fun cancelAll()

    /** A sync a few seconds from now; more changes meanwhile are covered by the same sync. */
    fun syncSoon()

    /** A sync as soon as there is a connection ("check now", pull to refresh). */
    fun syncNow()

    /**
     * A sync in a couple of seconds, for a change that carries an invitation or an answer: the
     * server sends the mail when it receives it, so it must not wait for the ordinary debounce.
     */
    fun syncPromptly()
}

/**
 * WorkManager syncs. Every sync needs a connection; the periodic one also waits for a battery that
 * is not low, and a failing one backs off exponentially, so a bad connection does not drain it.
 */
@Singleton
class WorkManagerCalDavScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : CalDavSyncScheduler,
    CalDavSyncTrigger {
    private val connected = Constraints.Builder().setRequiredNetworkType(
        NetworkType.CONNECTED
    ).build()
    private val batteryFriendly = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    override fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<CalDavSyncWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(batteryFriendly)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        work().enqueueUniquePeriodicWork(
            CalDavSyncWorker.PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    override fun cancelAll() {
        listOf(
            CalDavSyncWorker.PERIODIC_NAME,
            CalDavSyncWorker.SOON_NAME,
            CalDavSyncWorker.PROMPT_NAME,
            CalDavSyncWorker.NOW_NAME
        )
            .forEach { work().cancelUniqueWork(it) }
    }

    override fun syncSoon() = once(CalDavSyncWorker.SOON_NAME, DEBOUNCE_SECONDS)

    override fun syncNow() = once(CalDavSyncWorker.NOW_NAME, 0)

    override fun syncPromptly() = once(CalDavSyncWorker.PROMPT_NAME, PROMPT_SECONDS)

    override fun localChange(promptly: Boolean) = if (promptly) syncPromptly() else syncSoon()

    /** A pending run is replaced (a later one means a later start); a running one is followed. */
    private fun once(name: String, delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<CalDavSyncWorker>()
            .setConstraints(connected)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        work().enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private fun work() = WorkManager.getInstance(context)

    private companion object {
        const val PERIOD_MINUTES = 30L
        const val DEBOUNCE_SECONDS = 10L
        const val PROMPT_SECONDS = 2L
        const val BACKOFF_SECONDS = 30L
    }
}
