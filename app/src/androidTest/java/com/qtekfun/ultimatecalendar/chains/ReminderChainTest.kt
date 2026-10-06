// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.chains

import android.content.Intent
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.notify.BootReceiver
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RF-07 and RF-08 from the provider to the system: a real event with a reminder gets an exact
 * alarm, the alarm rings and the notification appears; editing the event moves the alarm and
 * deleting it removes it; and the alarms come back after the process died and after the clock or
 * the zone changed. This is the chain that was broken when the event source was an empty stub.
 */
@HiltAndroidTest
class ReminderChainTest : ChainTest() {
    private fun reminderAt(start: ZonedDateTime) = start.minusMinutes(REMINDER_MINUTES).toInstant()
        .toEpochMilli()

    private fun seedWithReminder(title: String, start: ZonedDateTime): EventId = calendars.seed(
        calendars.timed(
            title,
            calendars.work,
            start,
            reminders = listOf(Reminder(REMINDER_MINUTES.toInt()))
        )
    )

    @Test
    fun aReminderOfARealEventSetsAnAlarmRingsAndShowsTheNotification() {
        val title = calendars.unique("Standup")
        // The reminder is 10 minutes before: it rings between one and two minutes from now.
        val start = inMinutes(REMINDER_MINUTES + 2)
        startProcess()

        seedWithReminder(title, start)

        waitForAlarmAt(reminderAt(start))
        // The heartbeat (RF-08) is armed while there is a reminder to protect.
        val now = System.currentTimeMillis()
        assertTrue(
            "the heartbeat's alarm is missing",
            alarmTimes().any { it in now + HEARTBEAT_MIN_MS..now + HEARTBEAT_MAX_MS }
        )

        waitUntil(SLOW_TIMEOUT_MS) { title in notificationTitles() }
    }

    @Test
    fun editingTheEventMovesTheAlarmAndDeletingItRemovesTheAlarm() {
        val title = calendars.unique("Dentist")
        val start = inMinutes(THREE_HOURS)
        startProcess()
        val id = seedWithReminder(title, start)
        waitForAlarmAt(reminderAt(start))

        // The edit goes through the app's own source, as the editor does.
        val moved = start.plusHours(1)
        val event = checkNotNull(runBlocking { source.event(id) }.getOrNull())
        val time = EventTime.Timed(moved.toInstant(), moved.plusHours(1).toInstant(), moved.zone)
        runBlocking { source.update(event.copy(time = time)) }

        waitForAlarmAt(reminderAt(moved))
        waitUntil { !hasAlarmAt(reminderAt(start)) }

        runBlocking { source.delete(id) }

        waitUntil { !hasAlarmAt(reminderAt(moved)) }
        assertFalse(hasAlarmAt(reminderAt(start)))
    }

    @Test
    fun theAlarmsAreSetAgainWhenTheProcessStartsAfterTheSystemLostThem() {
        val title = calendars.unique("Flight")
        val start = inMinutes(THREE_HOURS)
        val first = startProcess()
        seedWithReminder(title, start)
        waitForAlarmAt(reminderAt(start))

        // A reboot: the process is gone and so are the alarms.
        killProcess(first)
        scheduler.schedule(emptyList(), alarmClock = false)
        waitUntil { !hasAlarmAt(reminderAt(start)) }

        // The process starts again: the start-up finds the event in the provider and plans it.
        startProcess()
        waitForAlarmAt(reminderAt(start))
    }

    @Test
    fun theBootReceiverPlansTheAlarmsAgainAfterAChangeOfTheClockOrTheZone() {
        val title = calendars.unique("Train")
        val start = inMinutes(THREE_HOURS)
        startProcess()
        seedWithReminder(title, start)
        waitForAlarmAt(reminderAt(start))
        scheduler.schedule(emptyList(), alarmClock = false)
        waitUntil { !hasAlarmAt(reminderAt(start)) }

        // The system tells the app the time zone changed; the receiver is the real one, made by
        // Hilt in this graph (a protected broadcast cannot be sent by an app, so it is called).
        BootReceiver().onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))

        waitForAlarmAt(reminderAt(start))
    }

    private companion object {
        const val REMINDER_MINUTES = 10L
        const val THREE_HOURS = 180L
        const val HEARTBEAT_MIN_MS = 25 * 60_000L
        const val HEARTBEAT_MAX_MS = 35 * 60_000L
    }
}
