// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Keeps the periodic check job in step with the interval of Settings. */
interface InvitationCheckScheduler {
    /** Replaces the periodic job by one every [interval], or removes it for manual only. */
    fun apply(interval: CheckInterval)
}

/** Runs the check with WorkManager: no network or charging constraints, it reads local data. */
class WorkManagerInvitationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : InvitationCheckScheduler {
    override fun apply(interval: CheckInterval) {
        val work = WorkManager.getInstance(context)
        val minutes = interval.minutes
        if (minutes == null) {
            work.cancelUniqueWork(InvitationCheckWorker.UNIQUE_NAME)
        } else {
            work.enqueueUniquePeriodicWork(
                InvitationCheckWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<InvitationCheckWorker>(minutes, TimeUnit.MINUTES).build()
            )
        }
    }
}
