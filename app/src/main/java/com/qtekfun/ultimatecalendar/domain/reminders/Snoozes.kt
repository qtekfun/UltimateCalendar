// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.ZoneId

/** What to do with the postponed reminders: ring, show now, or forget (see [Snoozes.resolve]). */
data class SnoozeState(
    val alarms: List<PlannedReminder>,
    val due: List<PlannedReminder>,
    val dropped: List<Long>
)

/**
 * Postponed reminders (RF-07). A postponed reminder is a [PlannedReminder] of its own, with an id
 * that no planned one has and one per occurrence, so postponing again replaces it. It is kept
 * on the phone and joins the plan, so it rings again after a restart or a reboot; and it is
 * checked against the events, so an edited or cancelled occurrence does not ring.
 */
object Snoozes {
    /** Planned ids are Ints widened to Long, so a Long this far below never collides. */
    private const val BASE = -(1L shl 40)
    private const val RANGE = 1L shl 33

    /** Whether [id] belongs to a postponed reminder rather than a planned one. */
    fun isSnooze(id: Long): Boolean = id in (BASE - RANGE)..(BASE + RANGE)

    /** [reminder] postponed by [option] from [now]. */
    fun snooze(reminder: PlannedReminder, option: SnoozeOption, now: Instant): PlannedReminder =
        reminder.copy(id = BASE + reminder.notificationKey, at = now.plus(option.duration))

    /**
     * Sorts [snoozed] against the current [occurrences]. One whose occurrence still exists (same
     * event, same start) is refreshed with the event's current title and links, and either rings
     * ([SnoozeState.alarms]) or is [SnoozeState.due]. One whose occurrence is gone waits, since
     * the source may be loading, and is dropped only once its time has passed.
     */
    fun resolve(
        snoozed: Collection<PlannedReminder>,
        occurrences: List<EventReminders>,
        zone: ZoneId,
        now: Instant
    ): SnoozeState {
        val alarms = mutableListOf<PlannedReminder>()
        val due = mutableListOf<PlannedReminder>()
        val dropped = mutableListOf<Long>()
        for (reminder in snoozed) {
            val current = occurrences.firstOrNull {
                it.instance.eventId == reminder.eventId &&
                    it.instance.time.startIn(zone) == reminder.start
            }
            val pending = reminder.at.isAfter(now)
            when {
                current != null -> {
                    val fresh = reminder.copy(
                        calendarId = current.instance.calendarId,
                        title = current.instance.title,
                        location = current.instance.location,
                        allDay = current.instance.time is EventTime.AllDay,
                        joinUrl = current.joinUrl
                    )
                    (if (pending) alarms else due).add(fresh)
                }

                !pending -> dropped.add(reminder.id)
            }
        }
        return SnoozeState(alarms.sortedBy { it.at }, due.sortedBy { it.at }, dropped)
    }

    /** The plan with the postponed alarms in it, soonest first and within the alarm cap. */
    fun merge(
        planned: List<PlannedReminder>,
        alarms: List<PlannedReminder>
    ): List<PlannedReminder> =
        (alarms + planned).sortedBy { it.at }.take(ReminderPlanner.MAX_ALARMS)

    /** [snoozed] without the one of the occurrence with [notificationKey] (it was dismissed). */
    fun without(snoozed: Collection<PlannedReminder>, notificationKey: Int): List<PlannedReminder> =
        snoozed.filter { it.notificationKey != notificationKey }
}
