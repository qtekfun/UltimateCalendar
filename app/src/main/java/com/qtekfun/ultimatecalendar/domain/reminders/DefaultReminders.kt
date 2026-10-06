// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder

/**
 * Gives the events that ask for "the calendar's default reminders" the reminders the user chose
 * for new events in the settings (RF-10), as the Android calendar app does with the default
 * reminder of its own preferences: the provider offers no default per calendar to read.
 */
object DefaultReminders {
    /**
     * [events] with the defaults added to the ones that use them: [timed] (minutes before the
     * start) for timed events and [allDay] for all-day ones. Reminders the event has of its own
     * stay; the others are returned as they are.
     */
    fun resolve(
        events: List<EventReminders>,
        timed: List<Int>,
        allDay: List<Int>
    ): List<EventReminders> = events.map { event ->
        if (event.usesDefaults) {
            val defaults = if (event.instance.time is EventTime.AllDay) allDay else timed
            event.copy(
                reminders = (event.reminders + defaults.map { Reminder(it) }).distinct(),
                usesDefaults = false
            )
        } else {
            event
        }
    }
}
