// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.firstrun

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.qtekfun.ultimatecalendar.notify.ReminderNotifier

/** What the system must allow for the app and its reminders to work (RF-01, RF-08). */
object ReminderPermissions {
    /** Both calendar permissions: reading shows events, writing edits and answers them. */
    val CALENDAR = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun calendarGranted(context: Context): Boolean = CALENDAR.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Whether a reminder would show: the permission (Android 13+), the app's switch in the system
     * settings and its reminders channel. Some phones block them from the settings, where the
     * permission dialog cannot help.
     */
    fun notificationsAllowed(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        val channel = manager.getNotificationChannel(ReminderNotifier.CHANNEL)
        return notificationPermissionGranted(context) && manager.areNotificationsEnabled() &&
            channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Whether the permission itself is granted, so only the settings can turn them back on. */
    fun notificationPermissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Granted at install on Android 13+ (USE_EXACT_ALARM); on 12 the user allows it. */
    fun exactAlarmsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun batteryExempt(context: Context): Boolean = context.getSystemService(
        PowerManager::class.java
    ).isIgnoringBatteryOptimizations(context.packageName)

    /**
     * The system dialog that exempts the app from battery optimisation. Play restricts it to some
     * apps; F-Droid does not, and reminders are exactly the case it exists for.
     */
    @SuppressLint("BatteryLife")
    fun askBatteryExemption(context: Context) = PhoneSettings.tryOpen(
        context,
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:${context.packageName}".toUri()
        )
    )

    /** Android 12: the screen where the user allows exact alarms. */
    fun askExactAlarms(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PhoneSettings.tryOpen(
                context,
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    "package:${context.packageName}".toUri()
                )
            )
        }
    }
}
