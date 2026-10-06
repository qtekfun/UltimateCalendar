// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeRole
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class VeventWriteTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-10-06T12:00:00Z")

    private fun write(base: IcsComponent?, vararg events: VeventFields) =
        VeventMapper.write(base, events.toList(), now, madrid)

    /** Unfolded lines of [component], written canonically. */
    private fun lines(component: IcsComponent) = IcsWriter.write(component)
        .replace("\r\n ", "").replace("\r\n", "\n").split("\n").filter { it.isNotEmpty() }

    private fun unfolded(text: String) = text.replace("\r\n", "\n").replace("\n ", "")
        .replace("\n\t", "").split("\n").filter { it.isNotEmpty() }

    @TestFactory
    fun `writing what was read changes nothing in any file of the corpus`() =
        IcsCorpus.files.map { file ->
            DynamicTest.dynamicTest("${file.parentFile?.name}/${file.name}") {
                val text = file.readText()
                val calendar = IcsParser.parse(text).single()
                val events = VeventMapper.read(calendar, madrid)

                val written = write(calendar, *events.toTypedArray())

                assertEquals(calendar, written)
                assertEquals(text, IcsWriter.write(written).take(text.length))
            }
        }

    @Test
    fun `changing the title touches only that line and the stamps`() {
        val text = IcsCorpus.text("nextcloud/timed-tzid-with-vtimezone.ics")
        val calendar = IcsParser.parse(text).single()
        val event = VeventMapper.read(calendar, madrid).single()

        val written = write(calendar, event.copy(title = "Dentist; the \"second\" one, 🦷"))

        val before = unfolded(text)
        val after = lines(written)
        val changed = after.filterNot { it in before }.toSet()
        assertEquals(
            setOf(
                "SUMMARY:Dentist\\; the \"second\" one\\, 🦷",
                "LAST-MODIFIED:20261006T120000Z",
                "DTSTAMP:20261006T120000Z",
                "SEQUENCE:3"
            ),
            changed
        )
        assertEquals(
            setOf(
                "SUMMARY:Dentist",
                "LAST-MODIFIED:20260928T142031Z",
                "DTSTAMP:20260928T142031Z",
                "SEQUENCE:2"
            ),
            before.filterNot { it in after }.toSet()
        )
        assertTrue("X-NEXTCLOUD-CUSTOM:kept untouched" in after)
    }

    @Test
    fun `moving a DURATION event writes DTSTART and DTEND and drops DURATION`() {
        val calendar = IcsCorpus.calendar("outlook/cancelled-with-duration.ics")
        val event = VeventMapper.read(calendar, madrid).single()
        val moved = EventTime.Timed(
            Instant.parse("2026-10-24T08:00:00Z"),
            Instant.parse("2026-10-24T09:30:00Z"),
            ZoneId.of("America/New_York")
        )

        val written = write(calendar, event.copy(time = moved))

        val out = lines(written)
        assertTrue("DTSTART;TZID=America/New_York:20261024T040000" in out)
        assertTrue("DTEND;TZID=America/New_York:20261024T053000" in out)
        assertFalse(out.any { it.startsWith("DURATION") })
        // The zone the file did not define is added before the event.
        val zone = written.components("VTIMEZONE").single()
        assertEquals("America/New_York", zone.property("TZID")?.value)
        assertEquals(VeventMapper.read(written, madrid).single().time, moved)
    }

    @Test
    fun `times are written as UTC, in a zone, floating-free, all-day or from a fixed offset`() {
        val base = VeventFields("u", "t", EventTime.Timed(now, now, ZoneOffset.UTC))
        val noZones = write(null, base)
        assertTrue("DTSTART:20261006T120000Z" in lines(noZones))
        assertEquals(emptyList<IcsComponent>(), noZones.components("VTIMEZONE"))

        val allDay = write(
            null,
            base.copy(
                time = EventTime.AllDay(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 11))
            )
        )
        assertTrue("DTSTART;VALUE=DATE:20261009" in lines(allDay))
        assertTrue("DTEND;VALUE=DATE:20261011" in lines(allDay))

        val fixed = write(null, base.copy(time = EventTime.Timed(now, now, ZoneOffset.ofHours(2))))
        // A bare offset is not a TZID: the moment is written in UTC.
        assertTrue("DTSTART:20261006T120000Z" in lines(fixed))

        val named = write(null, base.copy(time = EventTime.Timed(now, now, ZoneId.of("UTC"))))
        assertTrue("DTSTART:20261006T120000Z" in lines(named))

        val region = write(null, base.copy(time = EventTime.Timed(now, now, madrid)))
        assertEquals(1, region.components("VTIMEZONE").size)
    }

    @Test
    fun `a zone the file already defines is not added again`() {
        val calendar = IcsCorpus.calendar("nextcloud/timed-tzid-with-vtimezone.ics")
        val event = VeventMapper.read(calendar, madrid).single()

        val written = write(calendar, event.copy(location = "Elsewhere", time = event.time))

        assertEquals(1, written.components("VTIMEZONE").size)
    }

    @Test
    fun `a new event gets a UID, creation time and stamps`() {
        val event = VeventFields(
            uid = "new-1@ultimatecalendar",
            title = "Lunch",
            time = EventTime.Timed(
                Instant.parse("2026-10-07T11:00:00Z"),
                Instant.parse("2026-10-07T12:00:00Z"),
                madrid
            ),
            location = "Cafe, 1; corner",
            description = "Line one\nLine two",
            availability = Availability.FREE,
            rrule = "FREQ=WEEKLY;BYDAY=WE",
            exDates = setOf(OccurrenceKey.Moment(Instant.parse("2026-10-14T11:00:00Z"))),
            organizer = "ana@example.com",
            attendees = listOf(Attendee("ana@example.com", "Ana", isOrganizer = true)),
            reminders = listOf(Reminder(10))
        )

        val written = write(null, event)

        val out = lines(written)
        assertEquals(
            listOf(
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//UltimateCalendar//UltimateCalendar//EN"
            ),
            out.take(3)
        )
        assertTrue("UID:new-1@ultimatecalendar" in out)
        assertTrue("CREATED:20261006T120000Z" in out)
        assertTrue("DTSTAMP:20261006T120000Z" in out)
        assertTrue("LAST-MODIFIED:20261006T120000Z" in out)
        assertFalse(out.any { it.startsWith("SEQUENCE") })
        assertTrue("LOCATION:Cafe\\, 1\\; corner" in out)
        assertTrue("DESCRIPTION:Line one\\nLine two" in out)
        assertTrue("TRANSP:TRANSPARENT" in out)
        assertTrue("EXDATE;TZID=Europe/Madrid:20261014T130000" in out)
        assertTrue("ORGANIZER;CN=Ana:mailto:ana@example.com" in out)
        val attendee = "ATTENDEE;CN=Ana;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:" +
            "mailto:ana@example.com"
        assertTrue(attendee in out)
        assertTrue("TRIGGER:-PT10M" in out)
        // Every line is at most 75 octets once folded, and the text reads back as it was.
        assertTrue(IcsWriter.write(written).split("\r\n").all { it.toByteArray().size <= 75 })
        val back = VeventMapper.read(
            IcsParser.parse(IcsWriter.write(written)).single(),
            madrid
        ).single()
        assertEquals(event.copy(modifiedAt = now), back)
    }

    @Test
    fun `an empty title and no optional fields write only what exists`() {
        val written = write(null, VeventFields("u", "", EventTime.Timed(now, now, ZoneOffset.UTC)))

        val out = lines(written)
        assertFalse(out.any { it.startsWith("SUMMARY") })
        assertFalse(out.any { it.startsWith("LOCATION") || it.startsWith("DESCRIPTION") })
        assertFalse(out.any { it.startsWith("RRULE") || it.startsWith("EXDATE") })
    }

    @Test
    fun `attendees keep their original lines when unchanged and gain or lose others`() {
        val calendar = IcsCorpus.calendar("google/invitation-attendees.ics")
        val event = VeventMapper.read(calendar, madrid).single()
        val ana = event.attendees[0]
        val ben = event.attendees[1]
        val newcomer =
            Attendee("eve@example.com", "Eve", AttendeeRole.OPTIONAL, AttendeeStatus.NEEDS_ACTION)
        val benAccepted = ben.copy(status = AttendeeStatus.ACCEPTED)

        val written = write(calendar, event.copy(attendees = listOf(ana, benAccepted, newcomer)))

        val attendees = written.components("VEVENT").single().properties
            .filter { it.name == "ATTENDEE" }
        assertEquals(3, attendees.size)
        // Ana's line is untouched, with its X-NUM-GUESTS parameter.
        val first = calendar.components("VEVENT").single().properties.first {
            it.name == "ATTENDEE"
        }
        assertEquals(first, attendees[0])
        assertEquals("ACCEPTED", attendees[1].parameter("PARTSTAT")?.value)
        assertEquals("mailto:eve@example.com", attendees[2].value)
        assertEquals("OPT-PARTICIPANT", attendees[2].parameter("ROLE")?.value)
        assertEquals(
            listOf(ana, benAccepted, newcomer),
            VeventMapper.read(written, madrid).single().attendees
        )
    }

    @Test
    fun `attendees without an address are never dropped`() {
        val calendar = IcsCorpus.calendar("apple/invitation-email-parameter.ics")
        val event = VeventMapper.read(calendar, madrid).single()

        val written = write(calendar, event.copy(attendees = event.attendees.take(1)))

        val values = written.components("VEVENT").single().properties
            .filter { it.name == "ATTENDEE" }.map { it.value }
        assertEquals(
            listOf(
                "urn:uuid:11111111-2222-3333-4444-555555555555",
                "urn:uuid:99999999-9999-9999-9999-999999999999"
            ),
            values
        )
    }

    @Test
    fun `organizer is written and removed`() {
        val event = VeventFields("u", "t", EventTime.Timed(now, now, ZoneOffset.UTC))
        val withOrganizer = write(null, event.copy(organizer = "boss@example.com"))
        assertTrue("ORGANIZER:mailto:boss@example.com" in lines(withOrganizer))

        val removed = write(withOrganizer, event.copy(organizer = null))
        assertNull(removed.components("VEVENT").single().property("ORGANIZER"))
    }

    @Test
    fun `reminders keep their original alarms when unchanged`() {
        val calendar = IcsCorpus.calendar("nextcloud/alarms-forms.ics")
        val event = VeventMapper.read(calendar, madrid).single()
        val kept = calendar.components("VEVENT").single().components("VALARM")

        val written = write(
            calendar,
            event.copy(
                reminders = listOf(Reminder(15), Reminder(2, ReminderMethod.EMAIL), Reminder(0))
            )
        )

        val alarms = written.components("VEVENT").single().components("VALARM")
        // The 15 and 0 minute alarms stay as they were; the 1 day and 30 minute ones are gone.
        assertTrue(kept[0] in alarms && kept[8] in alarms)
        assertFalse(kept[1] in alarms || kept[2] in alarms)
        // Alarms the app cannot express are never lost.
        assertTrue(
            kept[3] in alarms && kept[4] in alarms && kept[5] in alarms && kept[6] in alarms &&
                kept[7] in alarms
        )
        val email = alarms.single { it.property("ACTION")?.value == "EMAIL" }
        assertEquals("-PT2M", email.property("TRIGGER")?.value)
        assertEquals("Reminder", email.property("SUMMARY")?.value)
        assertEquals(
            listOf(Reminder(15), Reminder(2, ReminderMethod.EMAIL), Reminder(0)),
            VeventMapper.read(written, madrid).single().reminders
        )
    }

    @Test
    fun `exceptions and extra dates are rewritten as one line per kind`() {
        val calendar = IcsCorpus.calendar("google/recurring-exdate-override.ics")
        val master = VeventMapper.read(calendar, madrid).first()
        val day = OccurrenceKey.Day(LocalDate.of(2026, 12, 1))
        val moments = setOf(
            OccurrenceKey.Moment(Instant.parse("2026-11-09T17:00:00Z")),
            OccurrenceKey.Moment(Instant.parse("2026-11-16T17:00:00Z"))
        )

        val written = write(
            calendar,
            master.copy(exDates = moments, rDates = setOf(day)),
            *VeventMapper.read(calendar, madrid).drop(1).toTypedArray()
        )

        val first = written.components("VEVENT").first()
        assertEquals(
            listOf("EXDATE;TZID=Europe/Madrid:20261109T180000,20261116T180000"),
            first.properties.filter { it.name == "EXDATE" }.map { IcsWriter.contentLine(it) }
        )
        assertEquals(
            listOf("RDATE;VALUE=DATE:20261201"),
            first.properties.filter { it.name == "RDATE" }.map { IcsWriter.contentLine(it) }
        )
        val back = VeventMapper.read(written, madrid).first()
        assertEquals(moments, back.exDates)
        assertEquals(setOf(day), back.rDates)
    }

    @Test
    fun `exceptions of an all-day or UTC series are written to match its start`() {
        val allDay = VeventFields(
            "u",
            "t",
            EventTime.AllDay(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)),
            exDates = setOf(
                OccurrenceKey.Day(LocalDate.of(2026, 10, 8)),
                OccurrenceKey.Day(LocalDate.of(2026, 10, 15))
            ),
            rrule = "FREQ=WEEKLY"
        )
        assertTrue("EXDATE;VALUE=DATE:20261008,20261015" in lines(write(null, allDay)))

        val utc = VeventFields(
            "u",
            "t",
            EventTime.Timed(now, now, ZoneOffset.UTC),
            exDates = setOf(OccurrenceKey.Moment(now.plusSeconds(86_400))),
            rrule = "FREQ=DAILY"
        )
        assertTrue("EXDATE:20261007T120000Z" in lines(write(null, utc)))

        // An exception at a moment in an all-day series falls back to UTC.
        val mixed = allDay.copy(exDates = setOf(OccurrenceKey.Moment(now)))
        assertTrue("EXDATE:20261006T120000Z" in lines(write(null, mixed)))
    }

    @Test
    fun `an override is matched by its RECURRENCE-ID, new ones are added and others removed`() {
        val calendar = IcsCorpus.calendar("google/recurring-exdate-override.ics")
        val (master, moved, _) = VeventMapper.read(calendar, madrid)
        val newKey = OccurrenceKey.Moment(Instant.parse("2026-11-09T17:00:00Z"))
        val added = master.copy(
            time = EventTime.Timed(
                Instant.parse("2026-11-09T18:00:00Z"),
                Instant.parse("2026-11-09T19:00:00Z"),
                madrid
            ),
            recurrenceId = newKey,
            rrule = null,
            exDates = emptySet()
        )

        val written = write(calendar, master, moved.copy(title = "Moved again"), added)

        val events = written.components("VEVENT")
        assertEquals(3, events.size)
        assertEquals("Moved again", events[1].property("SUMMARY")?.value)
        assertEquals(
            "RECURRENCE-ID;TZID=Europe/Madrid:20261109T180000",
            IcsWriter.contentLine(events[2].property("RECURRENCE-ID")!!)
        )
        assertNull(events[2].property("RRULE"))
        // The cancelled override of the file was not listed: it is gone.
        assertEquals(
            listOf("20261012T180000", "20261109T180000"),
            events.drop(1).map {
                it.property("RECURRENCE-ID")?.value
            }
        )
        // The master was not touched.
        assertEquals(calendar.components("VEVENT")[0], events[0])
    }

    @Test
    fun `a VEVENT the app cannot read is kept`() {
        val calendar = IcsParser.parse(
            "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:broken\nSUMMARY:No start\nEND:VEVENT\nEND:VCALENDAR\n"
        ).single()

        val written =
            write(calendar, VeventFields("u", "t", EventTime.Timed(now, now, ZoneOffset.UTC)))

        assertEquals(
            listOf("u", "broken"),
            written.components("VEVENT").map {
                it.property("UID")?.value
            }
        )
    }

    @Test
    fun `availability and status move together`() {
        val event = VeventFields("u", "t", EventTime.Timed(now, now, ZoneOffset.UTC))

        val tentative = write(null, event.copy(availability = Availability.TENTATIVE))
        assertTrue("STATUS:TENTATIVE" in lines(tentative))
        assertTrue("TRANSP:OPAQUE" in lines(tentative))
        assertEquals(
            Availability.TENTATIVE,
            VeventMapper.read(tentative, madrid).single().availability
        )

        val busy = write(tentative, event.copy(availability = Availability.BUSY))
        assertTrue("STATUS:CONFIRMED" in lines(busy))
        assertEquals(EventStatus.CONFIRMED, VeventMapper.read(busy, madrid).single().status)

        val cancelled =
            write(
                busy,
                event.copy(status = EventStatus.CANCELLED, availability = Availability.FREE)
            )
        assertTrue("STATUS:CANCELLED" in lines(cancelled))
        assertTrue("TRANSP:TRANSPARENT" in lines(cancelled))
        // A cancelled event stays cancelled whatever its availability says.
        val stillCancelled =
            write(
                cancelled,
                event.copy(status = EventStatus.CANCELLED, availability = Availability.TENTATIVE)
            )
        assertEquals(
            EventStatus.CANCELLED,
            VeventMapper.read(stillCancelled, madrid).single().status
        )
    }

    @Test
    fun `an unchanged event does not move its stamps, a changed one grows SEQUENCE`() {
        val calendar = IcsCorpus.calendar("google/all-day-multiday.ics")
        val event = VeventMapper.read(calendar, madrid).single()

        assertEquals(calendar, write(calendar, event))

        val changed = write(calendar, event.copy(location = null))
        val vevent = changed.components("VEVENT").single()
        assertNull(vevent.property("LOCATION"))
        assertEquals("3", vevent.property("SEQUENCE")?.value)
        assertEquals("20261006T120000Z", vevent.property("DTSTAMP")?.value)
    }
}
