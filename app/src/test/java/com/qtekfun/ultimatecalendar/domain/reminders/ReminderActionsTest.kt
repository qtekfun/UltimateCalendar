// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReminderActionsTest {
    private fun reminder(location: String?, joinUrl: String?) = PlannedReminder(
        1,
        EventId(1),
        CalendarId(1),
        "Event",
        location,
        Instant.EPOCH,
        false,
        Instant.EPOCH,
        joinUrl
    )

    @Test
    fun `an event with a video call offers to join it, even if it has a place`() {
        assertEquals(
            listOf(
                ReminderAction.Join("https://zoom.us/j/1"),
                ReminderAction.Snooze,
                ReminderAction.Dismiss
            ),
            ReminderActions.of(reminder("Office", "https://zoom.us/j/1"))
        )
    }

    @Test
    fun `an event with a place offers the map`() {
        assertEquals(
            listOf(
                ReminderAction.OpenMap("geo:0,0?q=Office"),
                ReminderAction.Snooze,
                ReminderAction.Dismiss
            ),
            ReminderActions.of(reminder("Office", null))
        )
    }

    @Test
    fun `an event with neither only offers to snooze or dismiss`() {
        assertEquals(
            listOf(ReminderAction.Snooze, ReminderAction.Dismiss),
            ReminderActions.of(reminder(null, null))
        )
    }

    @Test
    fun `the snooze choices are the three options`() {
        assertEquals(
            listOf(5L, 15L, 60L),
            ReminderActions.snoozeChoices().map { (it as ReminderAction.SnoozeFor).option.minutes }
        )
    }
}
