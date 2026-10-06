// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar

import android.app.Application
import androidx.work.Configuration
import com.qtekfun.ultimatecalendar.notify.KeepAliveController
import com.qtekfun.ultimatecalendar.notify.NotificationChannels
import com.qtekfun.ultimatecalendar.notify.ReminderCoordinator
import com.qtekfun.ultimatecalendar.sync.InvitationCheckCoordinator
import com.qtekfun.ultimatecalendar.sync.InvitationWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class UltimateCalendarApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var reminders: ReminderCoordinator

    @Inject
    lateinit var keepAlive: KeepAliveController

    @Inject
    lateinit var invitationChecks: InvitationCheckCoordinator

    @Inject
    lateinit var workerFactory: InvitationWorkerFactory

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // WorkManager starts on demand, after Hilt injected the factory (the manifest removes its
    // own initializer).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        // Alarms, recovery of missed reminders and robust mode live as long as the process.
        reminders.start(scope)
        keepAlive.start(scope)
        // The periodic invitation check, and the checks the provider's changes and the app
        // opening start (RF-06).
        invitationChecks.start(scope)
        registerActivityLifecycleCallbacks(OnActivityStarted(invitationChecks::onAppOpened))
    }
}
