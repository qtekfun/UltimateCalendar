// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.Reminder

/**
 * An occurrence of an event with the reminders of its event: what [ReminderPlanner] needs, since
 * an [EventInstance] does not carry them (every instance of a repetition has the same ones).
 */
data class EventReminders(
    val instance: EventInstance,
    val reminders: List<Reminder>,
    val description: String? = null,
    /**
     * The event asks for "the calendar's default reminders" (`Reminders.MINUTES_DEFAULT`), which
     * only the source knows to be so; [DefaultReminders.resolve] turns them into real ones.
     */
    val usesDefaults: Boolean = false
) {
    /** The video call of the event, found in its location or description. */
    val joinUrl: String? get() = ReminderLinks.videoCall(instance.location, description)
}
