// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.qtekfun.ultimatecalendar.notify.MissedReminderRecovery
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Gives WorkManager the workers that need injected dependencies (the app configures WorkManager
 * on demand, see `UltimateCalendarApp`). A class it does not know gets null, so that WorkManager
 * falls back to its own factory.
 */
@Singleton
class InvitationWorkerFactory @Inject constructor(
    private val checker: Provider<InvitationChecker>,
    private val recovery: Provider<MissedReminderRecovery>
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = if (workerClassName == InvitationCheckWorker::class.java.name) {
        InvitationCheckWorker(appContext, workerParameters, checker.get(), recovery.get())
    } else {
        null
    }
}
