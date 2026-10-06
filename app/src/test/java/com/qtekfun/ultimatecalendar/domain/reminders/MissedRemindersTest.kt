// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MissedRemindersTest {
    private val now = Instant.parse("2026-10-05T12:00:00Z")
    private val day = Duration.ofHours(24)

    private fun reminder(id: Long, at: Instant) = PlannedReminder(
        id,
        EventId(id),
        CalendarId(1),
        "Event",
        null,
        at.plusSeconds(600),
        allDay = false,
        at = at
    )

    @Test
    fun `missed reminders within the window come back, oldest first`() {
        val older = reminder(4, now.minus(Duration.ofHours(23)))
        val newer = reminder(2, now.minus(Duration.ofMinutes(5)))
        assertEquals(
            listOf(older, newer),
            MissedReminders.pick(listOf(newer, older), emptySet(), now, day)
        )
    }

    @Test
    fun `a reminder due right now counts, a future one does not`() {
        val due = reminder(2, now)
        val future = reminder(4, now.plusSeconds(1))
        assertEquals(listOf(due), MissedReminders.pick(listOf(due, future), emptySet(), now, day))
    }

    @Test
    fun `reminders older than the window are left alone`() {
        val edge = reminder(2, now.minus(day))
        val old = reminder(4, now.minus(Duration.ofHours(30)))
        assertEquals(
            emptyList<PlannedReminder>(),
            MissedReminders.pick(listOf(edge, old), emptySet(), now, day)
        )
    }

    @Test
    fun `shown ones are not repeated, but the same id at another time is a new reminder`() {
        val shownAt = now.minus(Duration.ofHours(2))
        val moved = reminder(2, now.minus(Duration.ofHours(1)))
        val shown = setOf(ShownReminder(2, shownAt))
        assertEquals(
            listOf(moved),
            MissedReminders.pick(listOf(reminder(2, shownAt), moved), shown, now, day)
        )
    }

    @Test
    fun `an all-day reminder that showed counts as shown at whatever time it goes off now`() {
        val showedAt = now.minus(Duration.ofHours(5))
        val allDay = reminder(2, now.minus(Duration.ofHours(1))).copy(allDay = true)
        val shown = setOf(ShownReminder(2, showedAt))
        assertEquals(true, MissedReminders.wasShown(allDay, shown))
        assertEquals(
            emptyList<PlannedReminder>(),
            MissedReminders.pick(listOf(allDay), shown, now, day)
        )
        // Another all-day reminder, or a timed one with the same id, is not the one that showed.
        assertEquals(false, MissedReminders.wasShown(allDay.copy(id = 3), shown))
        assertEquals(false, MissedReminders.wasShown(allDay.copy(allDay = false), shown))
    }

    @Test
    fun `a closed window recovers nothing`() {
        assertEquals(
            emptyList<PlannedReminder>(),
            MissedReminders.pick(
                listOf(reminder(2, now.minusSeconds(1))),
                emptySet(),
                now,
                Duration.ZERO
            )
        )
    }

    @Test
    fun `records older than the window can be forgotten`() {
        assertEquals(now.minus(day), MissedReminders.keepAfter(now, day))
    }
}
