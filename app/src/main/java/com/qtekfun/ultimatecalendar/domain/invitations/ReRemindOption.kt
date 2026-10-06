// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderPlanner
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** A moment at which an invitation that is still unanswered reminds the user again (T40). */
enum class ReRemindMoment {
    /** One day before the event starts. */
    DAY_BEFORE,

    /** One hour before the event starts; for an all-day event, the morning of its day. */
    HOUR_BEFORE;

    /**
     * When this moment is for an event with [time]. A timed event keeps the wall-clock time of
     * its own zone for "a day before" (so a daylight saving change in between does not shift it
     * by an hour), and is exactly 60 minutes before for "an hour before". An all-day event has
     * no start time: "a day before" is [allDayTime] of the previous day and "an hour before"
     * [allDayTime] of its own day, both in the phone's [zone], the way [ReminderPlanner] places
     * all-day alerts (60 minutes before midnight would be 23:00 the night before, a bad time to
     * be reminded).
     */
    fun fireAt(time: EventTime, allDayTime: LocalTime, zone: ZoneId): Instant = when (this) {
        DAY_BEFORE -> when (time) {
            is EventTime.Timed -> time.start.atZone(time.zone).minusDays(1).toInstant()
            is EventTime.AllDay -> ReminderPlanner.fireAt(time, MINUTES_PER_DAY, allDayTime, zone)
        }

        HOUR_BEFORE -> ReminderPlanner.fireAt(
            time,
            if (time is EventTime.Timed) MINUTES_PER_HOUR else 0,
            allDayTime,
            zone
        )
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 1_440
    }
}

/** What Settings offers for invitations nobody answered: when to remind again (off by default). */
enum class ReRemindOption(val moments: List<ReRemindMoment>) {
    OFF(emptyList()),
    DAY_BEFORE(listOf(ReRemindMoment.DAY_BEFORE)),
    HOUR_BEFORE(listOf(ReRemindMoment.HOUR_BEFORE)),
    BOTH(listOf(ReRemindMoment.DAY_BEFORE, ReRemindMoment.HOUR_BEFORE))
}
