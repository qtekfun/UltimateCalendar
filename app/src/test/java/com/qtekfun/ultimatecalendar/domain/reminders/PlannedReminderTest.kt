// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class PlannedReminderTest {
    private val start = Instant.parse("2026-10-05T10:00:00Z")

    private fun reminder(id: Long, event: Long, start: Instant) = PlannedReminder(
        id,
        EventId(event),
        CalendarId(1),
        "Event",
        null,
        start,
        false,
        start.minusSeconds(600 * id)
    )

    @Test
    fun `all the reminders of an occurrence share one notification, others do not`() {
        assertEquals(reminder(1, 5, start).notificationKey, reminder(2, 5, start).notificationKey)
        assertNotEquals(
            reminder(1, 5, start).notificationKey,
            reminder(1, 6, start).notificationKey
        )
        assertNotEquals(
            reminder(1, 5, start).notificationKey,
            reminder(1, 5, start.plusSeconds(3_600)).notificationKey
        )
    }

    @Test
    fun `reminders with the same data are equal and hash alike`() {
        val a = reminder(1, 5, start)
        val b = reminder(1, 5, start)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(a, a.copy())
        assertNotEquals(a, a.copy(title = "Other"))
        assertEquals(true, a.toString().contains("Event"))
    }
}
