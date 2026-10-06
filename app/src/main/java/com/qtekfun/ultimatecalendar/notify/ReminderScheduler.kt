// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import com.qtekfun.ultimatecalendar.domain.reminders.PlannedReminder
import com.qtekfun.ultimatecalendar.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFERENCES = "reminders"
private const val KEY_SCHEDULED = "scheduled"

/**
 * Turns planned reminders into alarms (RF-08): exact alarms, or alarm clocks in the aggressive
 * mode, which no battery saver delays (to be measured on ColorOS, T02b). Alarms of a previous
 * plan that are no longer wanted are cancelled.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val heartbeat: HeartbeatScheduler
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** False on Android 12 until the user allows exact alarms; granted at install on 13+. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun schedule(reminders: List<PlannedReminder>, alarmClock: Boolean) {
        val wanted = reminders.associateBy { it.id }
        val previous = preferences.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().mapNotNull {
            it.toLongOrNull()
        }
        (previous - wanted.keys).forEach { alarms.cancel(pendingIntent(it, null)) }
        wanted.values.forEach { set(it, alarmClock) }
        preferences.edit { putStringSet(KEY_SCHEDULED, wanted.keys.map(Long::toString).toSet()) }
        // No reminder left: the heartbeat stops too.
        heartbeat.update(reminders)
    }

    private fun set(reminder: PlannedReminder, alarmClock: Boolean) {
        val intent = pendingIntent(reminder.id, reminder)
        val at = reminder.at.toEpochMilli()
        when {
            canScheduleExact() && alarmClock -> alarms.setAlarmClock(
                AlarmManager.AlarmClockInfo(at, openApp()),
                intent
            )

            canScheduleExact() -> alarms.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                at,
                intent
            )

            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    /** What the system opens from its "next alarm" display. */
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun pendingIntent(id: Long, reminder: PlannedReminder?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction("reminder:$id")
        reminder?.let { ReminderReceiver.describe(intent, it) }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
