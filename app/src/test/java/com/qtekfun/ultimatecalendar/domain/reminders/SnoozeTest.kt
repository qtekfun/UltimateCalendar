// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SnoozeTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-10-05T10:00:00Z")

    private fun timed(id: Long, start: String, title: String = "Event $id") = EventReminders(
        EventInstance(
            EventId(id),
            CalendarId(7),
            title,
            EventTime.Timed(Instant.parse(start), Instant.parse(start).plusSeconds(3_600), madrid),
            location = "Room $id"
        ),
        listOf(Reminder(10)),
        description = "Call: https://zoom.us/j/$id"
    )

    private fun planned(event: EventReminders) =
        ReminderPlanner.planAll(listOf(event), LocalTime.of(9, 0), madrid).single()

    private fun snoozed(
        event: EventReminders,
        option: SnoozeOption = SnoozeOption.FIFTEEN_MINUTES
    ) = Snoozes.snooze(planned(event), option, now)

    @Test
    fun `the options are 5 minutes, 15 minutes and 1 hour`() {
        assertEquals(listOf(5L, 15L, 60L), SnoozeOption.entries.map { it.duration.toMinutes() })
        assertEquals(SnoozeOption.ONE_HOUR, SnoozeOption.fromMinutes(60))
        assertNull(SnoozeOption.fromMinutes(7))
    }

    @Test
    fun `postponing moves the reminder and gives it an id of its own per occurrence`() {
        val event = timed(1, "2026-10-05T10:30:00Z")
        val reminder = planned(event)
        val later = Snoozes.snooze(reminder, SnoozeOption.FIVE_MINUTES, now)
        assertEquals(now.plusSeconds(300), later.at)
        assertEquals(reminder.start, later.start)
        assertTrue(Snoozes.isSnooze(later.id))
        assertFalse(Snoozes.isSnooze(reminder.id))
        assertFalse(Snoozes.isSnooze(Long.MIN_VALUE))
        // Postponing a postponed reminder replaces it; another occurrence has another id.
        assertEquals(later.id, Snoozes.snooze(later, SnoozeOption.ONE_HOUR, now).id)
        assertNotEquals(later.id, snoozed(timed(2, "2026-10-05T10:30:00Z")).id)
    }

    @Test
    fun `an occurrence that still exists rings, with its current details`() {
        val event = timed(1, "2026-10-05T10:30:00Z")
        val later = snoozed(event)
        val edited = timed(1, "2026-10-05T10:30:00Z", "Renamed").let {
            it.copy(instance = it.instance.copy(location = null), description = null)
        }
        val state = Snoozes.resolve(listOf(later), listOf(edited), madrid, now)
        assertEquals(listOf("Renamed"), state.alarms.map { it.title })
        assertNull(state.alarms.single().location)
        assertNull(state.alarms.single().joinUrl)
        assertEquals(emptyList<PlannedReminder>(), state.due)
        assertEquals(emptyList<Long>(), state.dropped)
    }

    @Test
    fun `a postponed reminder whose time passed is due`() {
        val event = timed(1, "2026-10-05T10:30:00Z")
        val later = snoozed(event)
        val state = Snoozes.resolve(listOf(later), listOf(event), madrid, later.at)
        assertEquals(listOf(later.at), state.due.map { it.at })
        assertEquals(emptyList<PlannedReminder>(), state.alarms)
        assertEquals("https://zoom.us/j/1", state.due.single().joinUrl)
    }

    @Test
    fun `an edited start or a cancelled event does not ring, and goes once its time passed`() {
        val later = snoozed(timed(1, "2026-10-05T10:30:00Z"))
        val moved = timed(1, "2026-10-05T11:30:00Z")
        val other = timed(2, "2026-10-05T10:30:00Z")
        val waiting = Snoozes.resolve(listOf(later), listOf(moved, other), madrid, now)
        assertEquals(SnoozeState(emptyList(), emptyList(), emptyList()), waiting)
        val past = Snoozes.resolve(listOf(later), listOf(moved), madrid, later.at.plusSeconds(1))
        assertEquals(SnoozeState(emptyList(), emptyList(), listOf(later.id)), past)
    }

    @Test
    fun `an occurrence of a repetition is matched by its own start`() {
        val first = timed(1, "2026-10-05T10:30:00Z")
        val second = timed(1, "2026-10-12T10:30:00Z")
        val later = snoozed(second)
        val state = Snoozes.resolve(listOf(later), listOf(first, second), madrid, now)
        assertEquals(listOf(second.instance.time.startIn(madrid)), state.alarms.map { it.start })
    }

    @Test
    fun `an all-day occurrence is matched by the start of its day in the phone zone`() {
        val day = EventReminders(
            EventInstance(
                EventId(3),
                CalendarId(7),
                "Holiday",
                EventTime.AllDay(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-13"))
            ),
            listOf(Reminder(900))
        )
        val later = snoozed(day, SnoozeOption.ONE_HOUR)
        assertTrue(later.allDay)
        assertEquals(1, Snoozes.resolve(listOf(later), listOf(day), madrid, now).alarms.size)
        // The phone moved to another zone: the occurrence starts at another instant.
        val elsewhere = Snoozes.resolve(listOf(later), listOf(day), ZoneId.of("Asia/Tokyo"), now)
        assertEquals(emptyList<PlannedReminder>(), elsewhere.alarms)
    }

    @Test
    fun `postponed alarms join the plan, soonest first, within the cap`() {
        val events = (1L..ReminderPlanner.MAX_ALARMS + 5).map {
            timed(it, Instant.parse("2026-11-01T10:00:00Z").plusSeconds(it * 60).toString())
        }
        val plan = ReminderPlanner.plan(events, LocalTime.of(9, 0), now, madrid)
        val later = snoozed(timed(900, "2026-10-05T10:30:00Z"))
        val merged = Snoozes.merge(plan, listOf(later))
        assertEquals(ReminderPlanner.MAX_ALARMS, merged.size)
        assertEquals(later, merged.first())
        assertEquals(plan.first(), merged[1])
    }

    @Test
    fun `dismissing forgets the postponed reminders of that occurrence only`() {
        val one = snoozed(timed(1, "2026-10-05T10:30:00Z"))
        val two = snoozed(timed(2, "2026-10-05T10:30:00Z"))
        assertEquals(listOf(two), Snoozes.without(listOf(one, two), one.notificationKey))
    }
}
