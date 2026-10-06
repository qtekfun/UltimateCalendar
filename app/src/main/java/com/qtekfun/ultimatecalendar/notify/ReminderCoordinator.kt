// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.domain.reminders.MissedReminders
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderEventSource
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderPlanner
import com.qtekfun.ultimatecalendar.domain.reminders.Snoozes
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the alarms in step with the events and the settings (RF-08): any change (an edit, a
 * sync, a setting) plans and schedules them again. [refresh] does it after a restart or a change
 * of the clock or time zone.
 */
@Singleton
@Suppress("LongParameterList")
class ReminderCoordinator @Inject constructor(
    private val events: ReminderEventSource,
    private val settings: ReminderSettingsSource,
    private val scheduler: ReminderScheduler,
    private val recovery: MissedReminderRecovery,
    private val snoozed: SnoozedReminders,
    private val shown: ShownReminders,
    private val time: ReminderTime
) {
    private val ticks = MutableStateFlow(0)

    /** How many plans were set, so [replan] knows when its own is done. */
    private val plans = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        // Whatever the system kept from showing while the app was stopped.
        scope.launch { recovery.recover() }
        scope.launch {
            combine(settings.settings, ticks) { current, _ -> current }
                .flatMapLatest { current ->
                    val now = time.now()
                    events.observe(now, now.plus(ReminderPlanner.HORIZON)).map { occurrences ->
                        val at = time.now()
                        // One that already showed is not set again: the clock went back, or an
                        // all-day one moved to another zone where it is still to come.
                        val showed = shown.all()
                        val planned = ReminderPlanner.plan(
                            occurrences,
                            current.allDayTime,
                            at,
                            time.zone()
                        ).filterNot { MissedReminders.wasShown(it, showed) }
                        // Postponed reminders ring from the same plan, so they survive a reboot.
                        val postponed = Snoozes.resolve(
                            snoozed.all(),
                            occurrences,
                            time.zone(),
                            at
                        )
                        Snoozes.merge(planned, postponed.alarms) to current.alarmClock
                    }
                }
                .collect { (reminders, alarmClock) ->
                    scheduler.schedule(reminders, alarmClock)
                    plans.value++
                }
        }
    }

    fun refresh() {
        ticks.value++
    }

    /**
     * Plans again and waits until the alarms are set, for callers whose process may end right
     * after (an alarm's receiver). Gives up after a few seconds: the next beat retries.
     */
    suspend fun replan() {
        val before = plans.value
        refresh()
        withTimeoutOrNull(REPLAN_TIMEOUT_MS) { plans.first { it > before } }
    }

    private companion object {
        const val REPLAN_TIMEOUT_MS = 5_000L
    }
}
