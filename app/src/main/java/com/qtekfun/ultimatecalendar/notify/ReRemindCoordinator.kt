// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.invitations.ReRemindLog
import com.qtekfun.ultimatecalendar.domain.invitations.Invitation
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAlert
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationReReminders
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindContext
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindPlanner
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies what [ReRemindPlanner] decides (T40): forgets and records re-reminders in Room, sets
 * their alarms and shows the ones that are due as ordinary invitation notifications, with the
 * same buttons. Runs after every invitation check (the check knows what is still pending, so an
 * answer given anywhere, a cancellation or a move changes the alarms), when the option or the
 * time of all-day reminders changes, at app start, after a reboot or a change of clock or zone,
 * on every heartbeat, and when an alarm rings. One at a time: the log is written before anything
 * shows, so a reminder shows at most once even if two triggers meet.
 */
@Singleton
@Suppress("LongParameterList")
class ReRemindCoordinator @Inject constructor(
    private val log: ReRemindLog,
    private val notified: NotifiedInvitations,
    private val settings: ReminderSettingsSource,
    private val time: ReminderTime,
    private val alarms: InvitationReReminderAlarms,
    private val surface: InvitationNotificationSurface
) : InvitationReReminders {
    private val running = Mutex()
    private val ticks = MutableStateFlow(0)

    /** Sets the alarms now and again whenever the settings change or [refresh] is called. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(settings.settings, ticks) { current, _ ->
                current
            }.collect { reconcileStored() }
        }
    }

    /** The clock, the zone or the system changed: plans again from what was last known. */
    fun refresh() {
        ticks.value++
    }

    /** Plans from the invitations of the last check, which needs no calendar access. */
    suspend fun reconcileStored() = reconcile(notified.load())

    override suspend fun reconcile(pending: List<Invitation>) = running.withLock {
        val current = settings.settings.first()
        val plan = ReRemindPlanner.plan(
            pending,
            log.all(),
            ReRemindContext(
                option = current.reRemind,
                allDayTime = current.allDayTime,
                zone = time.zone(),
                now = time.now(),
                window = Duration.ofHours(current.missedWindowHours.toLong())
            )
        )
        if (plan.save.isNotEmpty() || plan.delete.isNotEmpty()) log.apply(plan.save, plan.delete)
        alarms.schedule(plan.alarms, current.alarmClock)
        plan.due.forEach { surface.show(it, InvitationAlert.REMINDER) }
        if (plan.due.isNotEmpty()) surface.refreshSummary()
    }
}
