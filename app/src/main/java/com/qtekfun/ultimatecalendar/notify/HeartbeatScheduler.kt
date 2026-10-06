// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.qtekfun.ultimatecalendar.domain.reminders.Heartbeat
import com.qtekfun.ultimatecalendar.domain.reminders.PlannedReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The heartbeat's alarm (RF-08). An exact alarm, not an alarm clock: it shows nothing, so it must
 * not appear as the "next alarm" on the lock screen; Doze lets one through every few minutes,
 * well under its half hour. Real reminders keep the alarm clock in the aggressive mode.
 */
@Singleton
class HeartbeatScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** Beats again in half an hour while [pending] has a reminder still to come; else stops. */
    fun update(pending: List<PlannedReminder>) {
        val next = Heartbeat.next(clock.instant(), pending)
        if (next == null) {
            pendingIntent(PendingIntent.FLAG_NO_CREATE)?.let {
                alarms.cancel(it)
                it.cancel()
            }
            return
        }
        val intent = pendingIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val at = next.toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    /** Whether the next beat is set, for the diagnosis and the tests. */
    fun isScheduled(): Boolean = pendingIntent(PendingIntent.FLAG_NO_CREATE) != null

    private fun pendingIntent(flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, HeartbeatReceiver::class.java).setAction(ACTION),
        flag or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val REQUEST = -2
        const val ACTION = "com.qtekfun.ultimatecalendar.HEARTBEAT"
    }
}
