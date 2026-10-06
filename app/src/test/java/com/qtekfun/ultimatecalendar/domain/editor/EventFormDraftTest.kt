// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.data.source.toDraft
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.form
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.madrid
import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.work
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.recurrence.CustomRepeat
import com.qtekfun.ultimatecalendar.domain.recurrence.RepeatEnd
import com.qtekfun.ultimatecalendar.domain.recurrence.RepeatPreset
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventFormDraftTest {
    private fun at(text: String) = LocalDateTime.parse(text)

    @Test
    fun `a valid form becomes the draft the source stores`() {
        val guest = Attendee.of("guest@example.com")
        val filled = form().copy(
            title = "  Team lunch ",
            location = " Cafe ",
            description = "",
            color = 0xFFD50000.toInt(),
            availability = Availability.FREE,
            attendees = listOf(guest),
            repeat = RepeatSetting.Preset(RepeatPreset.WEEKLY)
        )

        val draft = requireNotNull(filled.toDraft())

        assertEquals(work.id, draft.calendarId)
        assertEquals("Team lunch", draft.title)
        assertEquals("Cafe", draft.location)
        assertNull(draft.description)
        assertEquals(0xFFD50000.toInt(), draft.color)
        assertEquals(Availability.FREE, draft.availability)
        assertEquals("FREQ=WEEKLY", draft.rrule)
        assertEquals(listOf(guest), draft.attendees)
        assertEquals(listOf(Reminder(10)), draft.reminders)
        assertEquals(
            EventTime.Timed(
                Instant.parse("2026-03-10T09:30:00Z"),
                Instant.parse("2026-03-10T10:30:00Z"),
                madrid
            ),
            draft.time
        )
    }

    @Test
    fun `an empty title is allowed and stored empty`() {
        val draft = requireNotNull(form().toDraft())

        assertEquals("", draft.title)
        assertNull(draft.location)
    }

    @Test
    fun `without a calendar there is nothing to save to`() {
        val noCalendar = form().copy(calendarId = null)

        assertEquals(setOf(FormIssue.NO_CALENDAR), noCalendar.issues)
        assertNull(noCalendar.toDraft())
    }

    @Test
    fun `a valid form has no issues`() {
        assertTrue(form().isValid)
        assertEquals(emptySet<FormIssue>(), form().issues)
    }

    @Test
    fun `a repetition that ends before the event starts is a problem`() {
        val custom = CustomRepeat(end = RepeatEnd.ON_DATE, until = LocalDate.parse("2026-03-09"))
        val broken = form().copy(repeat = RepeatSetting.Custom(custom))

        assertEquals(setOf(FormIssue.REPEAT_ENDS_BEFORE_START), broken.issues)
        assertNull(broken.toDraft())
    }

    @Test
    fun `a repetition ending the day of the event is fine`() {
        val custom = CustomRepeat(end = RepeatEnd.ON_DATE, until = LocalDate.parse("2026-03-10"))

        assertTrue(form().copy(repeat = RepeatSetting.Custom(custom)).isValid)
    }

    @Test
    fun `a rule with its end date left over but ending never does not complain`() {
        val custom = CustomRepeat(end = RepeatEnd.NEVER, until = LocalDate.parse("2026-01-01"))

        assertTrue(form().copy(repeat = RepeatSetting.Custom(custom)).isValid)
    }

    @Test
    fun `a timed rule ends at the last second of its day in the event's zone`() {
        val custom = CustomRepeat(end = RepeatEnd.ON_DATE, until = LocalDate.parse("2026-04-01"))
        val timed = form().copy(repeat = RepeatSetting.Custom(custom))

        // Madrid is on summer time (UTC+2) by then: the day ends at 21:59:59 UTC.
        assertEquals(
            "FREQ=WEEKLY;BYDAY=TU;UNTIL=20260401T215959Z",
            timed.rrule()
        )
    }

    @Test
    fun `an all-day rule ends on a date`() {
        val custom = CustomRepeat(end = RepeatEnd.ON_DATE, until = LocalDate.parse("2026-04-01"))
        val allDay = form().copy(repeat = RepeatSetting.Custom(custom)).withAllDay(true)

        assertEquals("FREQ=WEEKLY;BYDAY=TU;UNTIL=20260401", allDay.rrule())
    }

    @Test
    fun `a rule that counts keeps its count`() {
        val custom = CustomRepeat(end = RepeatEnd.AFTER_COUNT, count = 5)

        assertEquals(
            "FREQ=WEEKLY;BYDAY=TU;COUNT=5",
            form().copy(repeat = RepeatSetting.Custom(custom)).rrule()
        )
    }

    @Test
    fun `no repetition stores no rule`() {
        assertNull(form().rrule())
    }

    @Test
    fun `an event the user can draft is the event the source gets back`() {
        val draft = requireNotNull(form().copy(title = "Standup").toDraft())

        val event: Event = draft.toEvent(EventId(5), organizer = "me@example.com")

        assertNotNull(event)
        assertEquals(draft, event.toDraft())
        assertEquals("me@example.com", event.organizer)
    }

    @Test
    fun `the guests exclude the organizer`() {
        val organizer = Attendee.of("me@example.com", isOrganizer = true)
        val guest = Attendee.of("guest@example.com")

        val withOrganizer = form().copy(attendees = listOf(organizer, guest))

        assertEquals(listOf(guest), withOrganizer.guests)
        assertEquals(listOf(organizer, guest), requireNotNull(withOrganizer.toDraft()).attendees)
        assertEquals(at("2026-03-10T10:30"), withOrganizer.start.toLocalDateTime())
    }
}
