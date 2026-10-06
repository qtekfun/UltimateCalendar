// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The broadcasts [BootReceiver] reacts to; the manifest declares the same four (RF-08). */
object BootActions {
    /** Android lets apps start a foreground service from these two broadcasts only. */
    val SERVICE = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)

    val ALL = SERVICE + setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
}

/**
 * Alarms do not survive a restart or an update, and change meaning with the clock or the time
 * zone (RF-08). Receiving any of these starts the app, which plans every reminder again, and
 * after a restart or an update starts the robust mode's service if it is on.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var coordinator: ReminderCoordinator

    @Inject
    lateinit var keepAlive: KeepAliveController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BootActions.ALL) return
        coordinator.refresh()
        // Android lets apps start a foreground service from these two broadcasts only.
        if (intent.action in BootActions.SERVICE) {
            val result = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    if (keepAlive.wanted()) KeepAliveService.start(context)
                } finally {
                    result.finish()
                }
            }
        }
    }
}
