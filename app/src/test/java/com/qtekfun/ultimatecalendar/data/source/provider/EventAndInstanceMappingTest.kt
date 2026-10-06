// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventMappingTest {
    private val start = Instant.parse("2026-10-06T10:00:00Z")
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun row(vararg extra: Pair<String, Any?>): ProviderRow = mapOf(
        Events._ID to 4L,
        Events.CALENDAR_ID to 2L,
        Events.TITLE to "Lunch",
        Events.DTSTART to start.toEpochMilli(),
        Events.DTEND to start.plusSeconds(3_600).toEpochMilli(),
        Events.EVENT_TIMEZONE to "Europe/Madrid",
        Events.ALL_DAY to 0L
    ) + extra

    @Test
    fun `an event row becomes an event with its children`() {
        val attendee = Attendee.of("ana@example.com")
        val event = requireNotNull(
            EventMapping.toEvent(
                row(
                    Events.EVENT_LOCATION to "Cafe",
                    Events.DESCRIPTION to "Notes",
                    Events.EVENT_COLOR to 0x336699L,
                    Events.AVAILABILITY to Events.AVAILABILITY_FREE.toLong(),
                    Events.RRULE to "FREQ=DAILY",
                    Events.ORGANIZER to "me@example.com"
                ),
                listOf(attendee),
                listOf(Reminder(10))
            )
        )

        assertEquals(EventId(4), event.id)
        assertEquals(CalendarId(2), event.calendarId)
        assertEquals(EventTime.Timed(start, start.plusSeconds(3_600), madrid), event.time)
        assertEquals("Cafe", event.location)
        assertEquals("Notes", event.description)
        assertEquals(0xFF336699.toInt(), event.color)
        assertEquals(Availability.FREE, event.availability)
        assertEquals("FREQ=DAILY", event.rrule)
        assertEquals("me@example.com", event.organizer)
        assertEquals(listOf(attendee), event.attendees)
        assertEquals(listOf(Reminder(10)), event.reminders)
    }

    @Test
    fun `empty optional columns are absent and missing availability is busy`() {
        val event = requireNotNull(
            EventMapping.toEvent(
                row(Events.EVENT_LOCATION to "", Events.DESCRIPTION to "", Events.RRULE to " "),
                emptyList(),
                emptyList()
            )
        )

        assertNull(event.location)
        assertNull(event.description)
        assertNull(event.rrule)
        assertNull(event.color)
        assertEquals(Availability.BUSY, event.availability)
        assertEquals(
            Availability.TENTATIVE,
            EventMapping.availabilityOf(Events.AVAILABILITY_TENTATIVE)
        )
    }

    @Test
    fun `rows that cannot be shown are not events`() {
        assertNull(EventMapping.toEvent(row(Events.DELETED to 1L), emptyList(), emptyList()))
        assertNull(EventMapping.toEvent(row(Events._ID to null), emptyList(), emptyList()))
        assertNull(EventMapping.toEvent(row(Events.DTSTART to null), emptyList(), emptyList()))
        assertNull(EventMapping.toEvent(row(Events.CALENDAR_ID to null), emptyList(), emptyList()))
        assertNotNull(EventMapping.toEvent(row(Events.DELETED to 0L), emptyList(), emptyList()))
    }

    @Test
    fun `a row repeats with a rule or with extra dates`() {
        assertFalse(EventMapping.repeats(row()))
        assertTrue(EventMapping.repeats(row(Events.RRULE to "FREQ=DAILY")))
        assertTrue(EventMapping.repeats(row(Events.RDATE to "20261007T100000Z")))
        assertFalse(EventMapping.repeats(row(Events.RRULE to "", Events.RDATE to " ")))
    }

    @Test
    fun `a draft is written with every field, clearing the absent ones`() {
        val draft = EventDraft(
            calendarId = CalendarId(2),
            title = "Lunch",
            time = EventTime.AllDay(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7)),
            availability = Availability.TENTATIVE,
            attendees = listOf(Attendee.of("a@b.c")),
            reminders = emptyList()
        )

        val values = EventMapping.toValues(draft)

        assertEquals("Lunch", values[Events.TITLE])
        assertTrue(values.containsKey(Events.EVENT_LOCATION))
        assertNull(values[Events.EVENT_LOCATION])
        assertNull(values[Events.DESCRIPTION])
        assertNull(values[Events.EVENT_COLOR])
        assertNull(values[Events.RRULE])
        assertEquals(Events.AVAILABILITY_TENTATIVE, values[Events.AVAILABILITY])
        assertEquals(1, values[Events.HAS_ATTENDEE_DATA])
        assertEquals(0, values[Events.HAS_ALARM])
        assertEquals(1, values[Events.ALL_DAY])
    }

    @Test
    fun `a series is written with a duration and a flag for its reminders`() {
        val draft = EventDraft(
            CalendarId(2),
            "Gym",
            EventTime.Timed(start, start.plusSeconds(600), madrid),
            rrule = "FREQ=WEEKLY;COUNT=3",
            reminders = listOf(Reminder(5))
        )

        val values = EventMapping.toValues(draft)

        assertEquals("FREQ=WEEKLY;COUNT=3", values[Events.RRULE])
        assertNull(values[Events.DTEND])
        assertEquals("P600S", values[Events.DURATION])
        assertEquals(1, values[Events.HAS_ALARM])
        assertEquals(0, values[Events.HAS_ATTENDEE_DATA])
    }

    @Test
    fun `an event round trips through its draft`() {
        val event = requireNotNull(
            EventMapping.toEvent(
                row(Events.EVENT_LOCATION to "Cafe"),
                emptyList(),
                listOf(Reminder(1))
            )
        )

        val draft = EventMapping.draftOf(event)

        assertEquals(event, draft.toEvent(event.id))
    }

    @Test
    fun `writes never carry sync columns or the calendar and organizer`() {
        val draft = EventDraft(CalendarId(2), "x", EventTime.Timed(start, start, madrid))

        val columns = EventMapping.toValues(draft).keys

        val sync = setOf(Events._SYNC_ID, Events.SYNC_DATA1, Events.DIRTY, Events.CAL_SYNC1)
        assertTrue(columns.none { it in sync })
        assertFalse(Events.CALENDAR_ID in columns)
        assertFalse(Events.ORGANIZER in columns)
    }
}

class InstanceMappingTest {
    private val range = TimeRange(
        Instant.parse("2026-10-06T00:00:00Z"),
        Instant.parse("2026-10-07T00:00:00Z")
    )
    private val noon = Instant.parse("2026-10-06T10:00:00Z")

    private fun row(vararg extra: Pair<String, Any?>): ProviderRow = mapOf(
        Instances.EVENT_ID to 4L,
        Instances.CALENDAR_ID to 2L,
        Instances.BEGIN to noon.toEpochMilli(),
        Instances.END to noon.plusSeconds(3_600).toEpochMilli(),
        Instances.TITLE to "Lunch",
        Instances.ALL_DAY to 0L,
        Instances.EVENT_TIMEZONE to "Europe/Madrid"
    ) + extra

    @Test
    fun `an instance row becomes an instance`() {
        val instance = requireNotNull(
            InstanceMapping.toInstance(
                row(
                    Instances.EVENT_LOCATION to "Cafe",
                    Instances.EVENT_COLOR to 0x336699L,
                    Instances.RRULE to "FREQ=DAILY",
                    Instances.SELF_ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_ACCEPTED.toLong()
                ),
                range
            )
        )

        assertEquals(EventId(4), instance.eventId)
        assertEquals(CalendarId(2), instance.calendarId)
        assertEquals("Lunch", instance.title)
        assertEquals("Cafe", instance.location)
        assertEquals(0xFF336699.toInt(), instance.color)
        assertTrue(instance.isRecurring)
        assertEquals(AttendeeStatus.ACCEPTED, instance.selfStatus)
        assertEquals(
            EventTime.Timed(noon, noon.plusSeconds(3_600), ZoneId.of("Europe/Madrid")),
            instance.time
        )
    }

    @Test
    fun `no self status means the user is not an attendee`() {
        assertNull(InstanceMapping.selfStatusOf(Attendees.ATTENDEE_STATUS_NONE))
        assertNull(InstanceMapping.selfStatusOf(null))
        assertEquals(
            AttendeeStatus.NEEDS_ACTION,
            InstanceMapping.selfStatusOf(Attendees.ATTENDEE_STATUS_INVITED)
        )
        assertNull(requireNotNull(InstanceMapping.toInstance(row(), range)).selfStatus)
    }

    @Test
    fun `a single event is not recurring`() {
        assertFalse(requireNotNull(InstanceMapping.toInstance(row(), range)).isRecurring)
    }

    @Test
    fun `an occurrence changed on its own is reported under its series and is still recurring`() {
        val instance = requireNotNull(
            InstanceMapping.toInstance(
                row(Instances.EVENT_ID to 9L, Instances.ORIGINAL_ID to 4L),
                range
            )
        )

        assertEquals(EventId(4), instance.eventId)
        assertTrue(instance.isRecurring)
    }

    @Test
    fun `an all-day instance is dated in UTC`() {
        val midnight = Instant.parse("2026-10-06T00:00:00Z").toEpochMilli()
        val instance = requireNotNull(
            InstanceMapping.toInstance(
                row(
                    Instances.ALL_DAY to 1L,
                    Instances.BEGIN to midnight,
                    Instances.END to midnight + 86_400_000L,
                    Instances.EVENT_TIMEZONE to "UTC"
                ),
                range
            )
        )

        assertEquals(
            EventTime.AllDay(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7)),
            instance.time
        )
    }

    @Test
    fun `cancelled and incomplete rows are dropped`() {
        assertNull(
            InstanceMapping.toInstance(
                row(Instances.STATUS to Events.STATUS_CANCELED.toLong()),
                range
            )
        )
        assertNotNull(
            InstanceMapping.toInstance(
                row(Instances.STATUS to Events.STATUS_CONFIRMED.toLong()),
                range
            )
        )
        assertNull(InstanceMapping.toInstance(row(Instances.BEGIN to null), range))
        assertNull(InstanceMapping.toInstance(row(Instances.EVENT_ID to null), range))
        assertNull(InstanceMapping.toInstance(row(Instances.CALENDAR_ID to null), range))
    }

    @Test
    fun `an instance with no end is a moment at its start`() {
        val instance = requireNotNull(InstanceMapping.toInstance(row(Instances.END to null), range))

        assertEquals(EventTime.Timed(noon, noon, ZoneId.of("Europe/Madrid")), instance.time)
    }

    @Test
    fun `the range is half open, although the provider includes both ends`() {
        val from = range.start.toEpochMilli()
        val to = range.end.toEpochMilli()

        assertTrue(InstanceMapping.overlaps(from - 1, from + 1, range))
        assertTrue(InstanceMapping.overlaps(to - 1, to + 1, range))
        assertFalse(InstanceMapping.overlaps(from - 10, from, range), "ends where the range starts")
        assertFalse(InstanceMapping.overlaps(to, to + 10, range), "starts where the range ends")
        assertTrue(InstanceMapping.overlaps(from, from, range), "a moment at the start")
        assertFalse(InstanceMapping.overlaps(to, to, range), "a moment at the end")
        assertTrue(InstanceMapping.overlaps(from - 10, to + 10, range))
    }
}
