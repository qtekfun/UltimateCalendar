// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar

import android.app.Application
import com.qtekfun.ultimatecalendar.notify.KeepAliveController
import com.qtekfun.ultimatecalendar.notify.NotificationChannels
import com.qtekfun.ultimatecalendar.notify.ReminderCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class UltimateCalendarApp : Application() {
    @Inject
    lateinit var reminders: ReminderCoordinator

    @Inject
    lateinit var keepAlive: KeepAliveController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        // Alarms, recovery of missed reminders and robust mode live as long as the process.
        reminders.start(scope)
        keepAlive.start(scope)
    }
}
