// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.qtekfun.ultimatecalendar.R

/**
 * The notification channels of the app, created in one place: one per kind, so the user can
 * silence each on its own. Reminders and new invitations interrupt; changes to events do not.
 * Creating a channel again changes nothing.
 */
object NotificationChannels {
    const val REMINDERS = "event_reminders"
    const val INVITATIONS = "invitations"
    const val CHANGES = "event_changes"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    REMINDERS,
                    context.getString(R.string.reminder_channel),
                    NotificationManager.IMPORTANCE_HIGH
                ),
                NotificationChannel(
                    INVITATIONS,
                    context.getString(R.string.invitations_channel),
                    NotificationManager.IMPORTANCE_HIGH
                ),
                NotificationChannel(
                    CHANGES,
                    context.getString(R.string.changes_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        )
    }
}
