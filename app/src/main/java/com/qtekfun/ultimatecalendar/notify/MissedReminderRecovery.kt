// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.domain.reminders.MissedReminders
import com.qtekfun.ultimatecalendar.domain.reminders.PlannedReminder
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderEventSource
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderPlanner
import com.qtekfun.ultimatecalendar.domain.reminders.ShownReminder
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Brings back reminders the system kept from showing (RF-08): some phones stop the app and drop
 * its alarms. Runs when the app starts, after each periodic check, on every alarm and every beat.
 */
@Singleton
class MissedReminderRecovery @Inject constructor(
    private val events: ReminderEventSource,
    private val settings: ReminderSettingsSource,
    private val shown: ShownReminders,
    private val notifier: ReminderNotifier,
    private val zone: SystemZone,
    private val clock: Clock
) {
    // The alarm, the check and the start can all run it at once: one at a time shows each once.
    private val running = Mutex()

    /** Records that a reminder showed at its time, so it is never brought back. */
    suspend fun markShown(reminder: PlannedReminder) =
        shown.add(listOf(ShownReminder(reminder.id, reminder.at)))

    suspend fun recover() = running.withLock {
        val current = settings.settings.first()
        val now = clock.instant()
        val window = Duration.ofHours(current.missedWindowHours.toLong())
        val planned = ReminderPlanner.planAll(
            events.observe(now.minus(window), now.plus(ReminderPlanner.HORIZON)).first(),
            current.allDayTime,
            zone.current()
        )
        val missed = MissedReminders.pick(planned, shown.all(), now, window)
        // The first time nothing was recorded: what is past already showed, or was missed long
        // enough ago. Bringing it all back on the first run would be a flood.
        val firstRun = !shown.baselineDone()
        if (!firstRun) missed.forEach { notifier.show(it, missed = true) }
        shown.add(missed.map { ShownReminder(it.id, it.at) })
        if (firstRun) shown.setBaselineDone()
        // Kept for the longest window offered, so widening it later repeats nothing.
        shown.forgetBefore(MissedReminders.keepAfter(now, maxOf(window, LONGEST_WINDOW)))
    }

    private companion object {
        val LONGEST_WINDOW: Duration = Duration.ofHours(48)
    }
}
