// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.reminders.PlannedReminder
import com.qtekfun.ultimatecalendar.domain.reminders.Snoozes
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * An alarm of a reminder (RF-07): shows it, records that it showed and, since the app is
 * running now, brings back any the system kept from showing (RF-08).
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject
    lateinit var notifier: ReminderNotifier

    @Inject
    lateinit var recovery: MissedReminderRecovery

    @Inject
    lateinit var testReminder: TestReminder

    override fun onReceive(context: Context, intent: Intent) {
        val reminder = read(intent) ?: return
        if (Snoozes.isSnooze(reminder.id)) {
            fireSnoozed(reminder)
            return
        }
        notifier.show(reminder, missed = false)
        val pending = goAsync()
        scope.launch {
            try {
                // The wizard's test reminder is not planned: only its arrival matters.
                if (reminder.id == TestReminder.ID) {
                    testReminder.arrived()
                } else {
                    recovery.markShown(reminder)
                }
                recovery.recover()
            } finally {
                pending.finish()
            }
        }
    }

    /** A postponed reminder rings: unless the recovery showed it already, it shows now. */
    private fun fireSnoozed(reminder: PlannedReminder) {
        val pending = goAsync()
        scope.launch {
            try {
                recovery.fireSnoozed(reminder)
                recovery.recover()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val EXTRA_ID = "id"
        private const val EXTRA_EVENT = "event"
        private const val EXTRA_CALENDAR = "calendar"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LOCATION = "location"
        private const val EXTRA_START = "start"
        private const val EXTRA_ALL_DAY = "all_day"
        private const val EXTRA_AT = "at"
        private const val EXTRA_JOIN = "join"

        /** What the notification needs, carried by the alarm. */
        fun describe(intent: Intent, reminder: PlannedReminder) {
            intent.putExtra(EXTRA_ID, reminder.id)
                .putExtra(EXTRA_EVENT, reminder.eventId.value)
                .putExtra(EXTRA_CALENDAR, reminder.calendarId.value)
                .putExtra(EXTRA_TITLE, reminder.title)
                .putExtra(EXTRA_LOCATION, reminder.location)
                .putExtra(EXTRA_START, reminder.start.toEpochMilli())
                .putExtra(EXTRA_ALL_DAY, reminder.allDay)
                .putExtra(EXTRA_AT, reminder.at.toEpochMilli())
                .putExtra(EXTRA_JOIN, reminder.joinUrl)
        }

        /** The reminder [describe] put in [intent], or null when it carries none. */
        fun read(intent: Intent): PlannedReminder? {
            if (!intent.hasExtra(EXTRA_ID) || !intent.hasExtra(EXTRA_EVENT)) return null
            return PlannedReminder(
                id = intent.getLongExtra(EXTRA_ID, 0),
                eventId = EventId(intent.getLongExtra(EXTRA_EVENT, 0)),
                calendarId = CalendarId(intent.getLongExtra(EXTRA_CALENDAR, 0)),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                location = intent.getStringExtra(EXTRA_LOCATION),
                start = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_START, 0)),
                allDay = intent.getBooleanExtra(EXTRA_ALL_DAY, false),
                at = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_AT, 0)),
                joinUrl = intent.getStringExtra(EXTRA_JOIN)
            )
        }
    }
}
