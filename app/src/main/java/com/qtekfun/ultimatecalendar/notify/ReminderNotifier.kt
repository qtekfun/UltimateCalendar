// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.reminders.PlannedReminder
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderAction
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderActions
import com.qtekfun.ultimatecalendar.domain.reminders.SnoozeOption
import com.qtekfun.ultimatecalendar.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Objects
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows the reminder of an event (RF-07); tapping it opens the app. A reminder brought back after
 * the system kept it from showing says when it was for (RF-08). Its buttons are join or map,
 * snooze and dismiss; snooze swaps them for 5 minutes, 15 minutes and 1 hour (Android shows
 * three at most). The test reminder of the wizard has none.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zone: SystemZone
) {
    fun show(reminder: PlannedReminder, missed: Boolean, snoozeChoices: Boolean = false) {
        // Before Android 13 notifications need no permission.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        NotificationChannels.ensureCreated(context)
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
            // Showing the snooze choices replaces the notification without ringing again.
            .setOnlyAlertOnce(snoozeChoices)
        if (reminder.id != TestReminder.ID) {
            val actions = if (snoozeChoices) {
                ReminderActions.snoozeChoices()
            } else {
                ReminderActions.of(reminder)
            }
            actions.forEach { builder.addAction(button(reminder, it)) }
        }
        try {
            NotificationManagerCompat.from(
                context
            ).notify(reminder.notificationKey, builder.build())
        } catch (_: SecurityException) {
            // The permission was revoked between the check and now: nothing to show.
        }
    }

    private fun button(
        reminder: PlannedReminder,
        action: ReminderAction
    ): NotificationCompat.Action {
        val (label, intent) = when (action) {
            is ReminderAction.Join -> R.string.reminder_action_join to view(reminder, action.url)

            is ReminderAction.OpenMap ->
                R.string.reminder_action_map to
                    view(reminder, action.uri)

            ReminderAction.Snooze ->
                R.string.reminder_action_snooze to
                    broadcast(reminder, ReminderActionReceiver.SNOOZE_CHOICES, null)

            is ReminderAction.SnoozeFor ->
                action.option.label to
                    broadcast(reminder, ReminderActionReceiver.SNOOZE, action.option.minutes)

            ReminderAction.Dismiss ->
                R.string.reminder_action_dismiss to
                    broadcast(reminder, ReminderActionReceiver.DISMISS, null)
        }
        return NotificationCompat.Action.Builder(0, context.getString(label), intent).build()
    }

    private val SnoozeOption.label: Int
        get() = when (this) {
            SnoozeOption.FIVE_MINUTES -> R.string.reminder_snooze_5
            SnoozeOption.FIFTEEN_MINUTES -> R.string.reminder_snooze_15
            SnoozeOption.ONE_HOUR -> R.string.reminder_snooze_60
        }

    private fun view(reminder: PlannedReminder, uri: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(reminder, uri),
            Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun broadcast(
        reminder: PlannedReminder,
        action: String,
        minutes: Long?
    ): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).setAction(action)
        ReminderReceiver.describe(intent, reminder)
        minutes?.let { intent.putExtra(ReminderActionReceiver.EXTRA_MINUTES, it) }
        return PendingIntent.getBroadcast(
            context,
            requestCode(reminder, "$action$minutes"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(reminder: PlannedReminder, what: String) =
        Objects.hash(reminder.notificationKey, what)

    private fun formatStart(start: Instant) = DateTimeFormatter.ofLocalizedDateTime(
        FormatStyle.SHORT
    ).format(start.atZone(zone.current()))

    private fun formatTime(at: Instant) =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at.atZone(zone.current()))

    companion object {
        const val CHANNEL = NotificationChannels.REMINDERS
        private const val GROUP = "event_reminders"
    }
}
