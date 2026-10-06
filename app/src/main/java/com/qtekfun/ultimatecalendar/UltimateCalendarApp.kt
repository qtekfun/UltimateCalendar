// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar

import android.app.Application
import androidx.work.Configuration
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
    lateinit var startup: AppStartup

    @Inject
    lateinit var workerFactory: InvitationWorkerFactory

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // WorkManager starts on demand, after Hilt injected the factory (the manifest removes its
    // own initializer).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        startup.start(scope)
        registerActivityLifecycleCallbacks(OnActivityStarted { startup.onActivityStarted(scope) })
    }
}
