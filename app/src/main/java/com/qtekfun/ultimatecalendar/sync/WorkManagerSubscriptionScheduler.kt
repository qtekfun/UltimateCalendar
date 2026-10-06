// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionScheduler
import com.qtekfun.ultimatecalendar.domain.subscriptions.SchedulePlan
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager refreshes of the subscriptions. The periodic one needs a connection and a battery
 * that is not low, backs off exponentially when it fails, and exists only while some subscription
 * refreshes by itself: [apply] cancels it with the last one. A request of the user needs only a
 * connection.
 */
@Singleton
class WorkManagerSubscriptionScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : SubscriptionScheduler {
    override fun apply(plan: SchedulePlan) {
        when (plan) {
            SchedulePlan.None -> work().cancelUniqueWork(SubscriptionRefreshWorker.PERIODIC_NAME)

            is SchedulePlan.Every -> {
                val request = PeriodicWorkRequestBuilder<SubscriptionRefreshWorker>(
                    plan.hours.toLong(),
                    TimeUnit.HOURS
                )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        BACKOFF_MINUTES,
                        TimeUnit.MINUTES
                    )
                    .build()
                // UPDATE changes the period of the work that exists without restarting its clock.
                work().enqueueUniquePeriodicWork(
                    SubscriptionRefreshWorker.PERIODIC_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }
    }

    override fun refreshNow() {
        val request = OneTimeWorkRequestBuilder<SubscriptionRefreshWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setInputData(
                Data.Builder().putBoolean(SubscriptionRefreshWorker.ALL_KEY, true).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        work().enqueueUniqueWork(
            SubscriptionRefreshWorker.NOW_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun work() = WorkManager.getInstance(context)

    private companion object {
        const val BACKOFF_MINUTES = 15L
    }
}
