// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import com.qtekfun.ultimatecalendar.data.ical.EventStatus
import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.data.local.entity.DavEventEntity
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeRole
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import com.qtekfun.ultimatecalendar.domain.recurrence.EventSeries
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceOverride
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoredSeriesTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val start = Instant.parse("2026-10-24T22:30:00Z")
    private val moment = OccurrenceKey.Moment(Instant.parse("2026-10-25T22:30:00Z"))
    private val day = OccurrenceKey.Day(LocalDate.parse("2026-10-26"))

    private fun master(time: EventTime, id: Long = 5) = Event(
        id = EventId(id),
        calendarId = CalendarId(3),
        title = "Night shift",
        time = time,
        location = "Plant",
        description = "Bring the badge",
        color = 0xFF2288,
        availability = Availability.TENTATIVE,
        rrule = "FREQ=DAILY;COUNT=5",
        organizer = "boss@example.com",
        attendees = listOf(
            Attendee.of(
                "boss@example.com",
                "Boss",
                AttendeeRole.REQUIRED,
                AttendeeStatus.ACCEPTED,
                true
            ),
            Attendee.of("ana@example.com", null, AttendeeRole.OPTIONAL, AttendeeStatus.TENTATIVE)
        ),
        reminders = listOf(Reminder(15), Reminder(60, ReminderMethod.EMAIL))
    )

    private val timed = EventTime.Timed(start, start.plusSeconds(3600), madrid)
    private val allDay = EventTime.AllDay(
        LocalDate.parse("2026-10-24"),
        LocalDate.parse("2026-10-26")
    )

    private fun ics(time: EventTime, overrides: List<OccurrenceOverride> = emptyList()) = IcsEvent(
        uid = "uid-9",
        status = EventStatus.TENTATIVE,
        sequence = 4,
        masterRecurrenceId = OccurrenceKey.Moment(start),
        series = EventSeries(
            event = master(time),
            exDates = setOf(moment, day),
            rDates = setOf(OccurrenceKey.Moment(start.plusSeconds(86_400 * 30))),
            overrides = overrides
        )
    )

    private fun row(event: IcsEvent) = StoredSeries.create(1, 3, "/cal/work/9.ics", event)
        .copy(id = 5, color = 0xFF2288)

    @Test
    fun `a series with everything survives the columns, and the row gives it its ids`() {
        val moved = master(
            EventTime.Timed(moment.at, moment.at.plusSeconds(1800), ZoneId.of("America/New_York"))
        ).copy(title = "Moved", rrule = null, color = null, location = null, description = null)
        val event = ics(
            timed,
            listOf(
                OccurrenceOverride(moment, moved),
                OccurrenceOverride(day, null),
                OccurrenceOverride(
                    OccurrenceKey.Day(LocalDate.parse("2026-10-27")),
                    moved.copy(time = allDay)
                )
            )
        )

        val read = StoredSeries.read(row(event))

        assertEquals(event, read)
    }

    @Test
    fun `an event the row does not hold the ids of takes the row's ids`() {
        val foreign = ics(
            timed,
            listOf(OccurrenceOverride(moment, master(timed, id = 77).copy(rrule = null)))
        ).let { it.copy(series = it.series.copy(event = it.series.event.copy(id = EventId(0)))) }

        val read = StoredSeries.read(row(foreign))

        assertEquals(EventId(5), read.series.event.id)
        assertEquals(EventId(5), read.series.overrides.single().replacement?.id)
        assertEquals(CalendarId(3), read.series.overrides.single().replacement?.calendarId)
    }

    @Test
    fun `writing keeps what is about this device and the server copy`() {
        val before = row(ics(timed)).copy(
            etag = "\"7\"",
            ics = "BEGIN:VCALENDAR",
            dirtyFields = 6,
            deleted = true,
            conflictTitle = "Server title",
            deletedOnServer = true,
            href = "/cal/work/other.ics"
        )

        val after = StoredSeries.write(before, ics(allDay).copy(masterRecurrenceId = null))

        assertEquals(
            before.copy(
                allDay = true,
                start = allDay.startDate.toEpochDay(),
                end = allDay.endDate.toEpochDay(),
                zone = null,
                windowStart = after.windowStart,
                windowEnd = after.windowEnd,
                masterRecurrenceId = null
            ),
            after
        )
        assertEquals(LocalDate.parse("2026-10-26"), LocalDate.ofEpochDay(after.end))
        assertNull(after.masterRecurrenceId)
    }

    @Test
    fun `a timed event that does not repeat is bounded by its own times`() {
        val single = ics(timed).let {
            it.copy(
                series = it.series.copy(
                    event = it.series.event.copy(rrule = null),
                    rDates = emptySet()
                )
            )
        }

        val stored = row(single)

        assertEquals(start.toEpochMilli(), stored.windowStart)
        assertEquals(start.plusSeconds(3600).toEpochMilli(), stored.windowEnd)
        assertEquals(start.toEpochMilli(), stored.start)
        assertEquals("Europe/Madrid", stored.zone)
    }

    @Test
    fun `a series that repeats or has RDATEs has no known end`() {
        val repeating = ics(timed).let {
            it.copy(series = it.series.copy(rDates = emptySet()))
        }
        val onlyExtraDates = repeating.let {
            it.copy(
                series = it.series.copy(
                    event = it.series.event.copy(rrule = null),
                    rDates = setOf(moment)
                )
            )
        }

        assertNull(row(repeating).windowEnd)
        assertNull(row(onlyExtraDates).windowEnd)
    }

    @Test
    fun `an all-day event is bounded with room for the viewer's zone on both sides`() {
        val single = ics(allDay).let {
            it.copy(
                series = it.series.copy(
                    event = it.series.event.copy(rrule = null),
                    rDates = emptySet()
                )
            )
        }

        val stored = row(single)
        val oneDay = 86_400_000L
        val fourteenHours = 14 * 3_600_000L

        assertEquals(allDay.startDate.toEpochDay() * oneDay - fourteenHours, stored.windowStart)
        assertEquals(allDay.endDate.toEpochDay() * oneDay + fourteenHours, stored.windowEnd)
        // Any zone's midnight of the days it covers falls inside, west or east.
        listOf("Pacific/Kiritimati", "Pacific/Pago_Pago").forEach {
            val first = allDay.startDate.atStartOfDay(ZoneId.of(it)).toInstant().toEpochMilli()
            val end = allDay.endDate.atStartOfDay(ZoneId.of(it)).toInstant().toEpochMilli()
            assertTrue(first >= stored.windowStart && end <= requireNotNull(stored.windowEnd), it)
        }
    }

    @Test
    fun `keys round trip as days and as moments`() {
        assertEquals(day, StoredKey.of(day).toKey())
        assertEquals(moment, StoredKey.of(moment).toKey())
        assertEquals("2026-10-26", StoredKey.of(day).day)
        assertEquals(moment.at.toEpochMilli(), StoredKey.of(moment).at)
    }

    @Test
    fun `a new row has no server copy`() {
        val created: DavEventEntity = StoredSeries.create(2, 3, "/cal/work/new.ics", ics(timed))

        assertNull(created.etag)
        assertNull(created.ics)
        assertEquals("uid-9", created.uid)
        assertEquals(0, created.dirtyFields)
        assertEquals(2L, created.accountId)
    }
}
