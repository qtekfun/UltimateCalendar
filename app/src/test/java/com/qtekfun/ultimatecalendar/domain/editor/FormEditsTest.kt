// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.cloud
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.defaults
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.form
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.madrid
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.newYork
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.work
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class FormEditsTest {
    private fun at(text: String) = LocalDateTime.parse(text)

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `moving the start moves the end so the event keeps its length`() {
        val moved = form().withStartTime(LocalTime.of(14, 0))

        assertEquals(at("2026-03-10T14:00"), moved.start.toLocalDateTime())
        assertEquals(at("2026-03-10T15:00"), moved.end.toLocalDateTime())
    }

    @Test
    fun `moving the start date moves a multi-day end with it`() {
        val twoDays = form(end = at("2026-03-12T11:30"))

        val moved = twoDays.withStartDate(date("2026-03-20"))

        assertEquals(at("2026-03-20T10:30"), moved.start.toLocalDateTime())
        assertEquals(at("2026-03-22T11:30"), moved.end.toLocalDateTime())
    }

    @Test
    fun `an end that carries past midnight moves to the next day`() {
        val moved = form().withStartTime(LocalTime.of(23, 30))

        assertEquals(at("2026-03-11T00:30"), moved.end.toLocalDateTime())
    }

    @Test
    fun `an end set before the start is a problem, not a silent change`() {
        val broken = form().withEndTime(LocalTime.of(9, 0))

        assertEquals(setOf(FormIssue.END_BEFORE_START), broken.issues)
        assertNull(broken.toEventTime())
        assertNull(broken.toDraft())
    }

    @Test
    fun `an event may end the moment it starts`() {
        val instant = form().withEndTime(LocalTime.of(10, 30))

        assertEquals(emptySet<FormIssue>(), instant.issues)
        assertEquals(Duration.ZERO, instant.length())
    }

    @Test
    fun `moving the start of a broken form leaves the end alone so it can be mended`() {
        val broken = form().withEndTime(LocalTime.of(9, 0))

        val mended = broken.withStartTime(LocalTime.of(8, 0))

        assertEquals(at("2026-03-10T09:00"), mended.end.toLocalDateTime())
        assertEquals(emptySet<FormIssue>(), mended.issues)
    }

    @Test
    fun `an end date before the start date is a problem`() {
        assertEquals(
            setOf(FormIssue.END_BEFORE_START),
            form().withEndDate(date("2026-03-09")).issues
        )
    }

    @Test
    fun `the spring gap keeps the elapsed time and skips the missing hour`() {
        // 2026-03-29: clocks in Madrid go from 02:00 to 03:00.
        val night = form(start = at("2026-03-29T00:30"), end = at("2026-03-29T01:30"))

        val moved = night.withStartTime(LocalTime.of(1, 30))

        assertEquals(at("2026-03-29T01:30"), moved.start.toLocalDateTime())
        // One hour after 01:30 is 03:30: 02:30 does not exist that night.
        assertEquals(at("2026-03-29T03:30"), moved.end.toLocalDateTime())
        assertEquals(Duration.ofHours(1), moved.length())
    }

    @Test
    fun `a start typed inside the spring gap moves forward to a time that exists`() {
        val moved = form().withStartDate(date("2026-03-29")).withStartTime(LocalTime.of(2, 30))

        assertEquals(at("2026-03-29T03:30"), moved.start.toLocalDateTime())
        assertEquals(at("2026-03-29T04:30"), moved.end.toLocalDateTime())
    }

    @Test
    fun `an end typed inside the spring gap moves forward too`() {
        val moved = form(start = at("2026-03-29T01:00"), end = at("2026-03-29T01:30"))
            .withEndTime(LocalTime.of(2, 15))

        assertEquals(at("2026-03-29T03:15"), moved.end.toLocalDateTime())
    }

    @Test
    fun `a time typed in the repeated autumn hour keeps the offset the time had`() {
        // 2026-10-25: 02:00-03:00 happens twice in Madrid. The start was at 10:30, after the
        // change (UTC+1), so 02:30 is the second pass.
        val moved = form().withStartDate(date("2026-10-25")).withStartTime(LocalTime.of(2, 30))

        val time = moved.toEventTime() as EventTime.Timed

        assertEquals(Instant.parse("2026-10-25T01:30:00Z"), time.start)
        assertEquals(Instant.parse("2026-10-25T02:30:00Z"), time.end)
        assertEquals(at("2026-10-25T03:30"), moved.end.toLocalDateTime())
    }

    @Test
    fun `an event across the repeated hour is saved as it was`() {
        // 00:30Z-01:30Z reads 02:30 (summer time) to 02:30 (winter time) on the wall.
        val original = EventTime.Timed(
            Instant.parse("2026-10-25T00:30:00Z"),
            Instant.parse("2026-10-25T01:30:00Z"),
            madrid
        )
        val stored = com.qtekfun.ultimatecalendar.domain.model.Event(
            id = com.qtekfun.ultimatecalendar.domain.model.EventId(1),
            calendarId = EditorFixtures.work.id,
            title = "Night shift",
            time = original
        )

        val form = EventForms.edit(stored, original, madrid, defaults)

        assertEquals(original, form.toDraft()?.time)
        assertEquals(original, form.copy(title = "Changed").toDraft()?.time)
        assertEquals(Duration.ofHours(1), form.length())
    }

    @Test
    fun `changing the zone keeps the wall-clock times and changes the moment`() {
        val inNewYork = form().withZone(newYork)

        assertEquals(at("2026-03-10T10:30"), inNewYork.start.toLocalDateTime())
        assertEquals(at("2026-03-10T11:30"), inNewYork.end.toLocalDateTime())
        val time = inNewYork.toEventTime() as EventTime.Timed
        // 10:30 in New York (UTC-4 from 8 March) is 14:30 UTC, not Madrid's 09:30 UTC.
        assertEquals(Instant.parse("2026-03-10T14:30:00Z"), time.start)
        assertEquals(newYork, time.zone)
    }

    @Test
    fun `a time that does not exist in the new zone moves forward`() {
        // 2026-03-08 02:30 does not exist in New York (clocks jump from 02:00 to 03:00).
        val madridNight = form(start = at("2026-03-08T02:30"), end = at("2026-03-08T03:30"))

        val moved = madridNight.withZone(newYork)

        assertEquals(at("2026-03-08T03:30"), moved.start.toLocalDateTime())
        assertEquals(at("2026-03-08T03:30"), moved.end.toLocalDateTime())
    }

    @Test
    fun `an all-day event ends on the date shown`() {
        val allDay = form().withAllDay(true)

        assertEquals(EventTime.AllDay(date("2026-03-10"), date("2026-03-11")), allDay.toEventTime())
    }

    @Test
    fun `a timed event ending at midnight becomes an all-day event of the day before`() {
        val late = form(start = at("2026-03-10T22:00"), end = at("2026-03-11T00:00"))

        val allDay = late.withAllDay(true)

        assertEquals(date("2026-03-10"), allDay.endDate)
        assertEquals(EventTime.AllDay(date("2026-03-10"), date("2026-03-11")), allDay.toEventTime())
    }

    @Test
    fun `a timed event over several days stays over those days`() {
        val long = form(end = at("2026-03-12T10:00")).withAllDay(true)

        assertEquals(EventTime.AllDay(date("2026-03-10"), date("2026-03-13")), long.toEventTime())
    }

    @Test
    fun `turning all-day off again brings the times back`() {
        val back = form().withAllDay(true).withAllDay(false)

        assertEquals(at("2026-03-10T10:30"), back.start.toLocalDateTime())
        assertEquals(at("2026-03-10T11:30"), back.end.toLocalDateTime())
    }

    @Test
    fun `turning all-day off on a one-day event gives the default length`() {
        val oneDay = EventForms.create(EditorFixtures.clock, madrid, defaults, work, allDay = true)
            .copy(
                start = at("2026-03-10T09:00").atZone(madrid),
                end = at("2026-03-10T09:00").atZone(madrid)
            )

        val timed = oneDay.withAllDay(false)

        assertEquals(at("2026-03-10T10:00"), timed.end.toLocalDateTime())
    }

    @Test
    fun `all-day dates move together like timed ones`() {
        val allDay = form(end = at("2026-03-12T10:30")).withAllDay(true)

        val moved = allDay.withStartDate(date("2026-03-20"))

        assertEquals(date("2026-03-22"), moved.endDate)
    }

    @Test
    fun `an all-day end before the start day is a problem`() {
        val allDay = form().withAllDay(true).withEndDate(date("2026-03-09"))

        assertEquals(setOf(FormIssue.END_BEFORE_START), allDay.issues)
    }

    @Test
    fun `untouched default reminders become the other kind's defaults`() {
        val settings = defaults.copy(timedReminders = listOf(10), allDayReminders = listOf(0, 1440))
        val timed = form().copy(defaults = settings, reminders = settings.reminders(false))

        val allDay = timed.withAllDay(true)

        assertEquals(listOf(Reminder(0), Reminder(1440)), allDay.reminders)
        assertEquals(listOf(Reminder(10)), allDay.withAllDay(false).reminders)
    }

    @Test
    fun `reminders the user chose become whole days when the event is all-day`() {
        val timed = form().copy(reminders = listOf(Reminder(30), Reminder(1500)))

        val allDay = timed.withAllDay(true)

        assertEquals(listOf(Reminder(1440), Reminder(2880)), allDay.reminders)
        assertEquals(listOf(Reminder(1440), Reminder(2880)), allDay.withAllDay(false).reminders)
    }

    @Test
    fun `whole-day reminders of one day collapse and keep their method`() {
        val timed = form().copy(
            reminders = listOf(Reminder(30), Reminder(60), Reminder(30, ReminderMethod.EMAIL))
        )

        val allDay = timed.withAllDay(true)

        assertEquals(listOf(Reminder(1440), Reminder(1440, ReminderMethod.EMAIL)), allDay.reminders)
    }

    @Test
    fun `a reminder is added once, in order, up to five`() {
        var edited = form().copy(reminders = emptyList())
        listOf(60, 5, 60, 30, 10, 15, 1440).forEach { edited = edited.withReminder(Reminder(it)) }

        assertEquals(listOf(5, 10, 15, 30, 60), edited.reminders.map { it.minutesBefore })
    }

    @Test
    fun `a reminder beyond four weeks is refused`() {
        val edited = form().copy(reminders = emptyList()).withReminder(Reminder(40_321))

        assertEquals(emptyList<Reminder>(), edited.reminders)
    }

    @Test
    fun `a reminder can be removed`() {
        val edited = form().withoutReminder(Reminder(10))

        assertEquals(emptyList<Reminder>(), edited.reminders)
    }

    @Test
    fun `moving to a calendar that cannot keep the color drops it`() {
        val colored = form().copy(color = 0xFFD50000.toInt())

        assertNull(colored.withCalendar(work).color)
        assertEquals(0xFFD50000.toInt(), colored.withCalendar(cloud).color)
        assertEquals(cloud.id, colored.withCalendar(cloud).calendarId)
    }

    @Test
    fun `an edit that changes nothing is the same form`() {
        val same = form()

        assertSame(same, same.withAllDay(false))
        assertEquals(same, same.withStartTime(same.start.toLocalTime()))
    }
}
