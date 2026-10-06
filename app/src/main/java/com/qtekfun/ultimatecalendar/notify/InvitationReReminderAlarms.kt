// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindEntry
import com.qtekfun.ultimatecalendar.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The alarms of the re-reminders of unanswered invitations (T40). */
interface InvitationReReminderAlarms {
    /** Makes [alarms] the only alarms set: the ones no longer wanted are cancelled. */
    fun schedule(alarms: List<ReRemindEntry>, alarmClock: Boolean)
}

private const val PREFERENCES = "invitation_re_reminders"
private const val KEY_SCHEDULED = "scheduled"

/**
 * [InvitationReReminderAlarms] on the alarm manager, the same way [ReminderScheduler] sets the
 * reminders of events: exact alarms (alarm clocks in the aggressive mode), or inexact while the
 * user has not allowed exact ones. The alarm carries only the key of the re-reminder: when it
 * rings, [InvitationReReminderReceiver] looks at the invitations again, so one answered or moved
 * meanwhile shows nothing. While any is set, the heartbeat keeps running (RF-08), so the app can
 * set them again if the system dropped them.
 */
@Singleton
class AndroidInvitationReReminderAlarms @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduler: ReminderScheduler,
    private val heartbeat: HeartbeatScheduler
) : InvitationReReminderAlarms {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun schedule(alarms: List<ReRemindEntry>, alarmClock: Boolean) {
        val wanted = alarms.associateBy { it.key.tag }
        val previous = preferences.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        (previous - wanted.keys).forEach { manager.cancel(pendingIntent(it)) }
        wanted.forEach { (tag, entry) ->
            val at = entry.at.toEpochMilli()
            val intent = pendingIntent(tag)
            when {
                scheduler.canScheduleExact() && alarmClock -> manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(at, openApp()),
                    intent
                )

                scheduler.canScheduleExact() ->
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)

                else -> manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        }
        preferences.edit { putStringSet(KEY_SCHEDULED, wanted.keys) }
        heartbeat.protectInvitations(wanted.isNotEmpty())
    }

    /** What the system opens from its "next alarm" display. */
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun pendingIntent(tag: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        tag.hashCode(),
        Intent(context, InvitationReReminderReceiver::class.java).setAction("re-remind:$tag"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
