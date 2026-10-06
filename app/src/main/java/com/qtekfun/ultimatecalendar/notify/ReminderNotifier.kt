// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.reminders.PlannedReminder
import com.qtekfun.ultimatecalendar.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows the reminder of an event (RF-07); tapping it opens the app. A reminder brought back after
 * the system kept it from showing says when it was for (RF-08). Its actions (snooze, dismiss,
 * map, join) and the other channels come with the notification task.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zone: SystemZone
) {
    fun show(reminder: PlannedReminder, missed: Boolean) {
        // Before Android 13 notifications need no permission.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        createChannel()
        val open = PendingIntent.getActivity(
            context,
            reminder.notificationKey,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val starts = if (reminder.allDay) {
            context.getString(R.string.reminder_text_all_day)
        } else {
            context.getString(R.string.reminder_text_timed, formatStart(reminder.start))
        }
        val text = if (missed) {
            context.getString(R.string.reminder_missed_text, starts, formatTime(reminder.at))
        } else {
            starts
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(reminder.title)
            .setContentText(text)
            .setSubText(reminder.location)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP)
        try {
            NotificationManagerCompat.from(
                context
            ).notify(reminder.notificationKey, builder.build())
        } catch (_: SecurityException) {
            // The permission was revoked between the check and now: nothing to show.
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.reminder_channel),
            NotificationManager.IMPORTANCE_HIGH
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun formatStart(start: Instant) = DateTimeFormatter.ofLocalizedDateTime(
        FormatStyle.SHORT
    ).format(start.atZone(zone.current()))

    private fun formatTime(at: Instant) =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at.atZone(zone.current()))

    companion object {
        const val CHANNEL = "event_reminders"
        private const val GROUP = "event_reminders"
    }
}
