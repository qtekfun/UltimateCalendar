// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

/** A button of a reminder's notification (RF-07). */
sealed interface ReminderAction {
    /** Opens the video call at [url]. */
    data class Join(val url: String) : ReminderAction

    /** Opens the map at the `geo:` [uri]. */
    data class OpenMap(val uri: String) : ReminderAction

    /** Shows the postpone choices in place of the buttons. */
    data object Snooze : ReminderAction

    /** Postpones the reminder by [option]. */
    data class SnoozeFor(val option: SnoozeOption) : ReminderAction

    data object Dismiss : ReminderAction
}

/**
 * Which buttons a reminder shows. Android shows three at most, so the video call wins over the
 * map when an event has both, and postponing is a second step with its own three buttons.
 */
object ReminderActions {
    fun of(reminder: PlannedReminder): List<ReminderAction> {
        val place = reminder.joinUrl?.let { ReminderAction.Join(it) }
            ?: ReminderLinks.map(reminder.location)?.let { ReminderAction.OpenMap(it) }
        return listOfNotNull(place, ReminderAction.Snooze, ReminderAction.Dismiss)
    }

    fun snoozeChoices(): List<ReminderAction> =
        SnoozeOption.entries.map { ReminderAction.SnoozeFor(it) }
}
