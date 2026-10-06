// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceOverride
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class IcsEventsTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val calendarId = CalendarId(7)
    private val eventId = EventId(42)
    private val now = Instant.parse("2026-10-06T12:00:00Z")

    private fun read(calendar: IcsComponent) = IcsEvents.read(calendar, calendarId, eventId, madrid)

    @Test
    fun `a series maps to the domain with its exceptions and overrides`() {
        val event = read(IcsCorpus.calendar("google/recurring-exdate-override.ics"))!!

        assertEquals("weekly-sync-0001@google.com", event.uid)
        assertEquals(EventStatus.CONFIRMED, event.status)
        assertEquals(1, event.sequence)
        val series = event.series
        assertEquals(eventId, series.event.id)
        assertEquals(calendarId, series.event.calendarId)
        assertEquals("Weekly sync", series.event.title)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", series.event.rrule)
        assertEquals(2, series.exDates.size)
        assertEquals(emptySet<OccurrenceKey>(), series.rDates)

        val (moved, cancelled) = series.overrides
        assertEquals(
            OccurrenceKey.Moment(Instant.parse("2026-10-12T16:00:00Z")),
            moved.recurrenceId
        )
        assertEquals("Weekly sync (late)", moved.replacement?.title)
        assertEquals(eventId, moved.replacement?.id)
        assertEquals(
            OccurrenceKey.Moment(Instant.parse("2026-10-19T16:00:00Z")),
            cancelled.recurrenceId
        )
        assertNull(cancelled.replacement)
    }

    @TestFactory
    fun `writing back what was read changes nothing in any file of the corpus`() =
        IcsCorpus.files.map { file ->
            DynamicTest.dynamicTest("${file.parentFile?.name}/${file.name}") {
                val calendar = IcsParser.parse(file.readText()).single()
                val event = read(calendar) ?: return@dynamicTest

                assertEquals(calendar, IcsEvents.write(calendar, event, now, madrid))
            }
        }

    @Test
    fun `editing the master leaves the overrides as they are`() {
        val calendar = IcsCorpus.calendar("google/recurring-exdate-override.ics")
        val event = read(calendar)!!

        val edited = event.copy(
            series = event.series.copy(event = event.series.event.copy(title = "Sync"))
        )
        val written = IcsEvents.write(calendar, edited, now, madrid)

        val events = written.components("VEVENT")
        assertEquals("Sync", events[0].property("SUMMARY")?.value)
        assertEquals(calendar.components("VEVENT").drop(1), events.drop(1))
        assertEquals(edited, read(written)?.copy(sequence = edited.sequence))
    }

    @Test
    fun `cancelling an occurrence adds an override with STATUS CANCELLED`() {
        val calendar = IcsCorpus.calendar("google/recurring-exdate-override.ics")
        val event = read(calendar)!!
        val key = OccurrenceKey.Moment(Instant.parse("2026-11-09T17:00:00Z"))

        val written = IcsEvents.write(
            calendar,
            event.copy(
                series = event.series.copy(
                    overrides =
                        event.series.overrides + OccurrenceOverride(key)
                )
            ),
            now,
            madrid
        )

        val added = written.components("VEVENT").last()
        assertEquals("CANCELLED", added.property("STATUS")?.value)
        assertEquals(
            "RECURRENCE-ID;TZID=Europe/Madrid:20261109T180000",
            IcsWriter.contentLine(added.property("RECURRENCE-ID")!!)
        )
        assertEquals("weekly-sync-0001@google.com", added.property("UID")?.value)
        assertEquals(key, read(written)?.series?.overrides?.last()?.recurrenceId)
        assertNull(read(written)?.series?.overrides?.last()?.replacement)
    }

    @Test
    fun `a cancelled all-day occurrence is written with a date`() {
        val calendar = IcsCorpus.calendar("nextcloud/rdate-daily-count.ics")
        val event = read(calendar)!!
        val key = OccurrenceKey.Day(LocalDate.of(2026, 11, 15))

        val written = IcsEvents.write(
            calendar,
            event.copy(series = event.series.copy(overrides = listOf(OccurrenceOverride(key)))),
            now,
            madrid
        )

        val added = written.components("VEVENT").last()
        assertEquals(
            "RECURRENCE-ID;VALUE=DATE:20261115",
            IcsWriter.contentLine(added.property("RECURRENCE-ID")!!)
        )
        assertEquals(
            "DTSTART;VALUE=DATE:20261115",
            IcsWriter.contentLine(added.property("DTSTART")!!)
        )
    }

    @Test
    fun `a file with only an override is read and written without inventing a master`() {
        val text = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:one-instance\n" +
            "RECURRENCE-ID:20261012T160000Z\nDTSTART:20261012T170000Z\nDTEND:20261012T180000Z\n" +
            "SUMMARY:Just this one\nEND:VEVENT\nEND:VCALENDAR\n"
        val calendar = IcsParser.parse(text).single()

        val event = read(calendar)!!

        assertEquals("Just this one", event.series.event.title)
        assertEquals(
            OccurrenceKey.Moment(Instant.parse("2026-10-12T16:00:00Z")),
            event.masterRecurrenceId
        )
        assertEquals(emptyList<OccurrenceOverride>(), event.series.overrides)
        val written = IcsEvents.write(
            calendar,
            event.copy(
                series = event.series.copy(event = event.series.event.copy(location = "Room 1"))
            ),
            now,
            madrid
        )
        val only = written.components("VEVENT").single()
        assertEquals("one-instance", only.property("UID")?.value)
        assertEquals("Room 1", only.property("LOCATION")?.value)
        assertEquals("20261012T160000Z", only.property("RECURRENCE-ID")?.value)
    }

    @Test
    fun `a cancelled master and a file without events`() {
        val cancelled = read(IcsCorpus.calendar("outlook/cancelled-with-duration.ics"))!!
        assertEquals(EventStatus.CANCELLED, cancelled.status)
        assertEquals(3, cancelled.sequence)

        assertNull(read(IcsParser.parse("BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR\n").single()))
    }

    @Test
    fun `a new event is created from the domain`() {
        val event = read(IcsCorpus.calendar("google/all-day-multiday.ics"))!!

        val written = IcsEvents.write(null, event, now, madrid)

        val back = read(written)!!
        assertNotNull(written.property("PRODID"))
        assertEquals(event.series.event.time, back.series.event.time)
        assertTrue(event.series.event.time is EventTime.AllDay)
        assertEquals(event.series.event, back.series.event)
        assertEquals(event.uid, back.uid)
    }
}
