// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qtekfun.ultimatecalendar.domain.reminders.SnoozeOption
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The snooze and dismiss buttons of a reminder's notification (RF-07). */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var handler: ReminderActionHandler

    @Inject
    lateinit var notifier: ReminderNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val reminder = ReminderReceiver.read(intent) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    SNOOZE_CHOICES -> notifier.show(reminder, missed = false, snoozeChoices = true)

                    SNOOZE -> {
                        SnoozeOption.fromMinutes(intent.getLongExtra(EXTRA_MINUTES, 0))
                            ?.let { handler.snooze(reminder, it) }
                        cancel(context, reminder.notificationKey)
                    }

                    DISMISS -> {
                        handler.dismiss(reminder)
                        cancel(context, reminder.notificationKey)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun cancel(context: Context, key: Int) =
        context.getSystemService(NotificationManager::class.java).cancel(key)

    companion object {
        const val SNOOZE_CHOICES = "com.qtekfun.ultimatecalendar.action.SNOOZE_CHOICES"
        const val SNOOZE = "com.qtekfun.ultimatecalendar.action.SNOOZE"
        const val DISMISS = "com.qtekfun.ultimatecalendar.action.DISMISS"
        const val EXTRA_MINUTES = "minutes"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
