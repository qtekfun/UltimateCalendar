// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.clock
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.defaults
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.madrid
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.newYork
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.work
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.recurrence.RepeatPreset
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventFormsTest {
    private fun at(text: String) = LocalDateTime.parse(text)

    @Test
    fun `a time already on a half hour is kept`() {
        assertEquals(at("2026-03-10T10:30"), EventForms.roundUpToSlot(at("2026-03-10T10:30")))
        assertEquals(at("2026-03-10T10:00"), EventForms.roundUpToSlot(at("2026-03-10T10:00")))
    }

    @Test
    fun `any other time rounds up to the next half hour`() {
        assertEquals(at("2026-03-10T10:30"), EventForms.roundUpToSlot(at("2026-03-10T10:07")))
        assertEquals(at("2026-03-10T11:00"), EventForms.roundUpToSlot(at("2026-03-10T10:31")))
        assertEquals(at("2026-03-11T00:00"), EventForms.roundUpToSlot(at("2026-03-10T23:45")))
    }

    @Test
    fun `seconds past a half hour round up too`() {
        assertEquals(
            at("2026-03-10T10:30"),
            EventForms.roundUpToSlot(at("2026-03-10T10:00:30"))
        )
    }

    @Test
    fun `a new event starts at the next half hour of the clock in the phone's zone`() {
        val form = EventForms.create(clock, madrid, defaults, work)

        // 09:07:30 UTC is 10:07:30 in Madrid.
        assertEquals(at("2026-03-10T10:30"), form.start.toLocalDateTime())
        assertEquals(at("2026-03-10T11:30"), form.end.toLocalDateTime())
        assertEquals(madrid, form.zone)
        assertEquals(work.id, form.calendarId)
        assertFalse(form.allDay)
    }

    @Test
    fun `the zone decides the day the clock is in`() {
        // 09:07 UTC is 05:07 in New York (UTC-4 since 8 March).
        val form = EventForms.create(clock, newYork, defaults, work)

        assertEquals(at("2026-03-10T05:30"), form.start.toLocalDateTime())
    }

    @Test
    fun `a tapped slot is the start and the settings give the length`() {
        val settings = defaults.copy(durationMinutes = 90)

        val form = EventForms.create(clock, madrid, settings, work, at = at("2026-04-02T14:00"))

        assertEquals(at("2026-04-02T14:00"), form.start.toLocalDateTime())
        assertEquals(at("2026-04-02T15:30"), form.end.toLocalDateTime())
    }

    @Test
    fun `a new event gets the default reminders of its kind`() {
        val settings = defaults.copy(
            timedReminders = listOf(5, 30),
            allDayReminders = listOf(0, 1440)
        )

        val timed = EventForms.create(clock, madrid, settings, work)
        val allDay = EventForms.create(clock, madrid, settings, work, allDay = true)

        assertEquals(listOf(Reminder(5), Reminder(30)), timed.reminders)
        assertEquals(listOf(Reminder(0), Reminder(1440)), allDay.reminders)
    }

    @Test
    fun `a new all-day event lasts the day it starts`() {
        val form = EventForms.create(clock, madrid, defaults, work, allDay = true)

        assertTrue(form.allDay)
        assertEquals(form.startDate, form.endDate)
        assertEquals(
            EventTime.AllDay(LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-11")),
            form.toEventTime()
        )
    }

    @Test
    fun `a new event has no calendar when none accepts events`() {
        val form = EventForms.create(clock, madrid, defaults, calendar = null)

        assertEquals(setOf(FormIssue.NO_CALENDAR), form.issues)
    }

    @Test
    fun `a timed event is edited in its own zone, not the phone's`() {
        val start = Instant.parse("2026-03-10T18:00:00Z")
        val event = event(EventTime.Timed(start, start.plusSeconds(3600), newYork))

        val form = EventForms.edit(event, event.time, madrid, defaults)

        assertEquals(newYork, form.zone)
        assertEquals(at("2026-03-10T14:00"), form.start.toLocalDateTime())
        assertEquals(at("2026-03-10T15:00"), form.end.toLocalDateTime())
    }

    @Test
    fun `an all-day event shows its last day, not the day after`() {
        val time = EventTime.AllDay(LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-13"))

        val form = EventForms.edit(event(time), time, madrid, defaults)

        assertTrue(form.allDay)
        assertEquals(LocalDate.parse("2026-03-10"), form.startDate)
        assertEquals(LocalDate.parse("2026-03-12"), form.endDate)
        assertEquals(time, form.toEventTime())
    }

    @Test
    fun `editing keeps every field of the event`() {
        val start = Instant.parse("2026-03-10T09:00:00Z")
        val guest = Attendee.of("guest@example.com")
        val stored = event(EventTime.Timed(start, start.plusSeconds(1800), madrid)).copy(
            title = "Review",
            location = "Room 4",
            description = "Slides",
            color = 0xFFD50000.toInt(),
            availability = Availability.FREE,
            organizer = "me@example.com",
            attendees = listOf(guest),
            reminders = listOf(Reminder(15), Reminder(60))
        )

        val form = EventForms.edit(stored, stored.time, madrid, defaults)

        assertEquals("Review", form.title)
        assertEquals("Room 4", form.location)
        assertEquals("Slides", form.description)
        assertEquals(0xFFD50000.toInt(), form.color)
        assertEquals(Availability.FREE, form.availability)
        assertEquals("me@example.com", form.organizer)
        assertEquals(listOf(guest), form.attendees)
        assertEquals(listOf(Reminder(15), Reminder(60)), form.reminders)
        assertEquals(RepeatSetting.NEVER, form.repeat)
    }

    @Test
    fun `an occurrence is edited at its own time`() {
        val first = Instant.parse("2026-03-10T09:00:00Z")
        val master = event(EventTime.Timed(first, first.plusSeconds(3600), madrid), "FREQ=DAILY")
        val third = EventTime.Timed(
            first.plusSeconds(2 * 86_400),
            first.plusSeconds(2 * 86_400 + 3600),
            madrid
        )

        val form = EventForms.edit(master, third, madrid, defaults)

        assertEquals(at("2026-03-12T10:00"), form.start.toLocalDateTime())
        assertEquals(RepeatSetting.Preset(RepeatPreset.DAILY), form.repeat)
    }

    @Test
    fun `a rule the app cannot read is kept as it came`() {
        val time = EventTime.Timed(
            Instant.parse("2026-03-10T09:00:00Z"),
            Instant.parse("2026-03-10T10:00:00Z"),
            madrid
        )
        val master = event(time, "FREQ=DAILY;BYHOUR=9")

        val form = EventForms.edit(master, time, madrid, defaults)

        assertEquals(RepeatSetting.Unsupported("FREQ=DAILY;BYHOUR=9"), form.repeat)
        assertEquals("FREQ=DAILY;BYHOUR=9", form.toDraft()?.rrule)
    }

    @Test
    fun `a custom rule is read back into the custom editor`() {
        val time = EventTime.Timed(
            Instant.parse("2026-03-10T09:00:00Z"),
            Instant.parse("2026-03-10T10:00:00Z"),
            madrid
        )
        val master = event(time, "FREQ=MONTHLY;INTERVAL=2;COUNT=6")

        val form = EventForms.edit(master, time, madrid, defaults)

        val custom = assertInstanceOf(RepeatSetting.Custom::class.java, form.repeat)
        assertEquals(2, custom.repeat.interval)
        assertEquals(6, custom.repeat.count)
        assertNull(form.copy(repeat = RepeatSetting.NEVER).rrule())
        assertEquals("FREQ=MONTHLY;INTERVAL=2;COUNT=6", form.rrule())
    }

    private fun event(time: EventTime, rrule: String? = null) = Event(
        id = EventId(9),
        calendarId = CalendarId(work.id.value),
        title = "",
        time = time,
        rrule = rrule
    )
}
