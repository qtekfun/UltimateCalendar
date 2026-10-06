// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Objects

/**
 * Which reminders to schedule: a pure decision, the alarms are elsewhere (RF-08). A timed event
 * reminds its `minutesBefore` before it starts; an all-day one reminds at [allDayTime] of the
 * day that many minutes (rounded up to whole days) before its first day, which is how Google
 * Calendar means "1 day before at 9:00" (900 minutes before midnight). Only alerts count: the
 * app does not send e-mails or SMS. Only future ones are planned.
 */
object ReminderPlanner {
    /** Android keeps at most 500 alarms per app; the rest are planned when these have rung. */
    const val MAX_ALARMS = 200

    /** How far ahead occurrences are read to plan: the heartbeat and the app renew it. */
    val HORIZON: Duration = Duration.ofDays(HORIZON_DAYS)

    private const val HORIZON_DAYS = 30L
    private const val MINUTES_PER_DAY = 1_440L
    private const val SECONDS_PER_MINUTE = 60L

    /** The next [MAX_ALARMS] reminders after [now], earliest first. */
    fun plan(
        events: List<EventReminders>,
        allDayTime: LocalTime,
        now: Instant,
        zone: ZoneId
    ): List<PlannedReminder> =
        planAll(events, allDayTime, zone).filter { it.at.isAfter(now) }.take(MAX_ALARMS)

    /** Every reminder of [events], past ones included, to find those that never showed. */
    fun planAll(
        events: List<EventReminders>,
        allDayTime: LocalTime,
        zone: ZoneId
    ): List<PlannedReminder> = events.flatMap { event ->
        val (instance, reminders) = event
        val start = instance.time.startIn(zone)
        // An all-day occurrence is a date, not an instant: its reminder keeps its id when the
        // phone changes zone, so one that already showed is not shown again (RF-08).
        val occurrence = (instance.time as? EventTime.AllDay)?.startDate?.toEpochDay()
            ?: start.toEpochMilli()
        reminders.filter { it.method == ReminderMethod.ALERT }
            .map { it.minutesBefore }
            .distinct()
            .map { minutes ->
                PlannedReminder(
                    id = Objects.hash(instance.eventId.value, occurrence, minutes).toLong(),
                    eventId = instance.eventId,
                    calendarId = instance.calendarId,
                    title = instance.title,
                    location = instance.location,
                    start = start,
                    allDay = instance.time is EventTime.AllDay,
                    at = fireAt(instance.time, minutes, allDayTime, zone),
                    joinUrl = event.joinUrl
                )
            }
    }.sortedBy { it.at }

    /** When a reminder [minutesBefore] the start of an event with [time] goes off. */
    fun fireAt(time: EventTime, minutesBefore: Int, allDayTime: LocalTime, zone: ZoneId): Instant =
        when (time) {
            is EventTime.Timed -> time.start.minusSeconds(minutesBefore * SECONDS_PER_MINUTE)

            is EventTime.AllDay -> {
                val days = (minutesBefore + MINUTES_PER_DAY - 1) / MINUTES_PER_DAY
                time.startDate.minusDays(days).atTime(allDayTime).atZone(zone).toInstant()
            }
        }
}
