// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.detail

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventDetailsTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val time = EventTime.Timed(
        Instant.parse("2026-10-08T07:00:00Z"),
        Instant.parse("2026-10-08T08:00:00Z"),
        madrid
    )

    private fun calendar(access: CalendarAccess, owner: String? = "me@x.org") = CalendarInfo(
        CalendarId(1),
        CalendarAccount("me@x.org", "com.example"),
        "Work",
        0xFF112233.toInt(),
        access,
        ownerEmail = owner
    )

    private fun event(
        vararg attendees: Attendee,
        color: Int? = null,
        location: String? = null,
        description: String? = null,
        rrule: String? = null
    ) = Event(
        EventId(5),
        CalendarId(1),
        "Review",
        time,
        location = location,
        description = description,
        color = color,
        rrule = rrule,
        organizer = "boss@x.org",
        attendees = attendees.toList(),
        reminders = listOf(Reminder(60), Reminder(10))
    )

    private fun invited(status: AttendeeStatus = AttendeeStatus.NEEDS_ACTION) =
        Attendee.of("me@x.org", status = status)

    private fun build(
        event: Event,
        calendar: CalendarInfo? = calendar(CalendarAccess.OWNER),
        aliases: List<String> = emptyList()
    ) = EventDetails.build(event, calendar, event.time, madrid, aliases)

    @Test
    fun `an invitee of a calendar that can answer may respond`() {
        val detail = build(event(invited(), Attendee.of("boss@x.org")))
        assertTrue(detail.canRespond)
        assertEquals(AttendeeStatus.NEEDS_ACTION, detail.self?.status)
    }

    @Test
    fun `the user is also found by an alias`() {
        val detail = build(
            event(Attendee.of("work@y.org")),
            calendar(CalendarAccess.OWNER, owner = null),
            aliases = listOf("Work@Y.org")
        )
        assertTrue(detail.canRespond)
    }

    @Test
    fun `not an attendee, no answer buttons`() {
        val detail = build(event(Attendee.of("boss@x.org")))
        assertFalse(detail.canRespond)
        assertNull(detail.self)
    }

    @Test
    fun `a read only calendar cannot answer, edit or delete`() {
        val detail = build(event(invited()), calendar(CalendarAccess.READ))
        assertFalse(detail.canRespond)
        assertFalse(detail.canEdit)
    }

    @Test
    fun `a calendar that only lets answer does not let edit`() {
        val detail = build(event(invited()), calendar(CalendarAccess.RESPOND))
        assertTrue(detail.canRespond)
        assertFalse(detail.canEdit)
    }

    @Test
    fun `an editable calendar lets edit`() {
        assertTrue(build(event(), calendar(CalendarAccess.EDIT)).canEdit)
        assertFalse(build(event(), calendar(CalendarAccess.CONTRIBUTE)).canEdit)
    }

    @Test
    fun `an unknown calendar allows nothing`() {
        val detail = build(event(invited()), calendar = null)
        assertFalse(detail.canRespond)
        assertFalse(detail.canEdit)
        assertNull(detail.calendar)
    }

    @Test
    fun `no attendees, no attendee block`() {
        assertNull(build(event()).attendees)
    }

    @Test
    fun `the color is the event's, else the calendar's`() {
        assertEquals(0xFF010203.toInt(), build(event(color = 0xFF010203.toInt())).color)
        assertEquals(0xFF112233.toInt(), build(event()).color)
        assertNull(build(event(), calendar = null).color)
    }

    @Test
    fun `reminders come with the closest first`() {
        assertEquals(listOf(10, 60), build(event()).reminders.map { it.minutesBefore })
    }

    @Test
    fun `a place is a map search and a web address opens the browser`() {
        val place = build(event(location = " Main St 1 "))
        assertEquals("Main St 1", place.location)
        assertEquals("geo:0,0?q=Main%20St%201", place.mapUri)
        assertNull(place.locationUrl)

        val web = build(event(location = "https://example.org/room"))
        assertNull(web.mapUri)
        assertEquals("https://example.org/room", web.locationUrl)
    }

    @Test
    fun `an empty place and description are nothing`() {
        val detail = build(event(location = "  ", description = " "))
        assertNull(detail.location)
        assertNull(detail.description)
        assertNull(detail.mapUri)
        assertEquals(emptyList<Any>(), detail.descriptionLinks)
    }

    @Test
    fun `the video call is found in the place or the description with the detector of reminders`() {
        val inDescription = build(
            event(
                description = "Join https://meet.google.com/abc-defg-hij, then https://example.org"
            )
        )
        assertEquals("https://meet.google.com/abc-defg-hij", inDescription.joinUrl)
        assertEquals(2, inDescription.descriptionLinks.size)

        val inPlace = build(event(location = "https://zoom.us/j/1", description = "no call"))
        assertEquals("https://zoom.us/j/1", inPlace.joinUrl)
        assertNull(build(event(description = "https://example.org")).joinUrl)
    }

    @Test
    fun `a repeating event says so and describes its rule`() {
        val series = build(event(rrule = "FREQ=WEEKLY;BYDAY=TH"))
        assertTrue(series.isSeries)
        assertEquals(2, series.repeat.size)
        assertFalse(build(event()).isSeries)
        assertEquals(emptyList<RepeatPhrase>(), build(event()).repeat)
    }

    @Test
    fun `answering shows the new answer for the user only`() {
        val detail =
            build(event(invited(), Attendee.of("boss@x.org", status = AttendeeStatus.ACCEPTED)))
        val answered = detail.answered(AttendeeStatus.TENTATIVE)
        assertEquals(AttendeeStatus.TENTATIVE, answered.self?.status)
        assertEquals(
            listOf(AttendeeStatus.TENTATIVE, AttendeeStatus.ACCEPTED),
            answered.event.attendees.map { it.status }
        )
        assertEquals(AttendeeStatus.NEEDS_ACTION, detail.self?.status)
        assertEquals(answered.event.attendees[1], detail.event.attendees[1])
    }

    @Test
    fun `all day reminders count back from midnight of the first day`() {
        val holiday = Event(
            EventId(6),
            CalendarId(1),
            "Holiday",
            EventTime.AllDay(LocalDate.parse("2026-12-24"), LocalDate.parse("2026-12-26")),
            reminders = listOf(Reminder(900), Reminder(0), Reminder(1440 + 480))
        )
        val lines = build(holiday).reminders
        assertEquals(
            listOf(0 to "00:00", 1 to "09:00", 2 to "16:00"),
            lines.map {
                requireNotNull(it.allDay).let { offset ->
                    offset.daysBefore to
                        "${offset.at}"
                }
            }
        )
    }
}
