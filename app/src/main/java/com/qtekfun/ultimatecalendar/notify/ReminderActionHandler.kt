// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.domain.reminders.PlannedReminder
import com.qtekfun.ultimatecalendar.domain.reminders.SnoozeOption
import com.qtekfun.ultimatecalendar.domain.reminders.Snoozes
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * What the buttons of a reminder do (RF-07). A postponed reminder is kept, set as an alarm at
 * once, and joined to the plan so it also survives a reboot; dismissing forgets the postponed
 * ones of the same occurrence.
 */
@Singleton
class ReminderActionHandler @Inject constructor(
    private val snoozed: SnoozedReminders,
    private val scheduler: ReminderScheduler,
    private val settings: ReminderSettingsSource,
    private val coordinator: ReminderCoordinator,
    private val clock: Clock
) {
    suspend fun snooze(reminder: PlannedReminder, option: SnoozeOption) {
        val later = Snoozes.snooze(reminder, option, clock.instant())
        snoozed.put(later)
        scheduler.scheduleOne(later, settings.settings.first().alarmClock)
        coordinator.replan()
    }

    suspend fun dismiss(reminder: PlannedReminder) {
        val all = snoozed.all()
        val kept = Snoozes.without(all, reminder.notificationKey)
        if (kept.size == all.size) return
        snoozed.remove((all - kept.toSet()).map { it.id })
        coordinator.replan()
    }
}
