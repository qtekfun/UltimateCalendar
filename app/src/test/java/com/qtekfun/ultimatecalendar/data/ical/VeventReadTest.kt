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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What the app understands of the real-world shaped files of the corpus. */
class VeventReadTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun read(path: String) = VeventMapper.read(IcsCorpus.calendar(path), madrid)

    private fun moment(text: String) = OccurrenceKey.Moment(Instant.parse(text))

    private fun timed(start: String, end: String, zone: ZoneId) =
        EventTime.Timed(Instant.parse(start), Instant.parse(end), zone)

    @Test
    fun `Google all-day event over several days`() {
        val event = read("google/all-day-multiday.ics").single()

        assertEquals("7q1k2m3n4p5r6s7t8u9v0w@google.com", event.uid)
        assertEquals("Team offsite", event.title)
        assertEquals(
            EventTime.AllDay(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16)),
            event.time
        )
        assertEquals(LocalDate.of(2026, 10, 15), (event.time as EventTime.AllDay).lastDate)
        assertEquals("Valencia, Spain", event.location)
        assertEquals("Conference trip\nBring the badge and the charger.", event.description)
        assertEquals(Availability.FREE, event.availability)
        assertEquals(EventStatus.CONFIRMED, event.status)
        assertEquals(2, event.sequence)
        assertEquals(Instant.parse("2026-10-01T07:59:59Z"), event.modifiedAt)
        assertNull(event.rrule)
    }

    @Test
    fun `Google series with exceptions, a moved occurrence and a cancelled one`() {
        val (master, moved, cancelled) = read("google/recurring-exdate-override.ics")

        val summer = ZoneId.of("Europe/Madrid")
        assertEquals(
            timed("2026-10-05T16:00:00Z", "2026-10-05T17:00:00Z", summer),
            master.time
        )
        assertEquals("FREQ=WEEKLY;BYDAY=MO", master.rrule)
        // The first Monday after the clocks go back is an hour later in UTC.
        assertEquals(
            setOf(moment("2026-10-26T17:00:00Z"), moment("2026-11-02T17:00:00Z")),
            master.exDates
        )
        assertNull(master.description)
        assertEquals(listOf(Reminder(10)), master.reminders)
        assertNull(master.recurrenceId)

        assertEquals(moment("2026-10-12T16:00:00Z"), moved.recurrenceId)
        assertEquals(timed("2026-10-12T17:30:00Z", "2026-10-12T18:30:00Z", summer), moved.time)
        assertEquals("Weekly sync (late)", moved.title)
        assertEquals("Meeting room 5", moved.location)

        assertEquals(moment("2026-10-19T16:00:00Z"), cancelled.recurrenceId)
        assertEquals(EventStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun `Google invitation with attendees in every state`() {
        val event = read("google/invitation-attendees.ics").single()

        assertEquals("ana@example.com", event.organizer)
        assertEquals(
            timed("2026-10-08T15:00:00Z", "2026-10-08T16:00:00Z", ZoneOffset.UTC),
            event.time
        )
        assertEquals(
            listOf(
                Attendee(
                    "ana@example.com",
                    "Ana Example",
                    isOrganizer = true,
                    status = AttendeeStatus.ACCEPTED
                ),
                Attendee("ben@example.com", "Ben Example"),
                Attendee(
                    "carla@example.com",
                    "Carla Example",
                    AttendeeRole.OPTIONAL,
                    AttendeeStatus.TENTATIVE
                ),
                Attendee("dan@example.com", "Dan Example", status = AttendeeStatus.DECLINED),
                Attendee(
                    "atlas@resource.example.com",
                    "Room Atlas",
                    AttendeeRole.RESOURCE,
                    AttendeeStatus.ACCEPTED
                )
            ),
            event.attendees
        )
        assertEquals(
            listOf(Reminder(60, ReminderMethod.EMAIL), Reminder(15, ReminderMethod.ALERT)),
            event.reminders
        )
        assertEquals(
            "Design review of the new calendar.\n\nJoin: https://meet.example.com/abc-defg-hij",
            event.description
        )
    }

    @Test
    fun `Nextcloud timed event keeps its own zone`() {
        val event = read("nextcloud/timed-tzid-with-vtimezone.ics").single()

        assertEquals(
            timed("2026-10-14T07:30:00Z", "2026-10-14T08:45:00Z", ZoneId.of("Europe/Berlin")),
            event.time
        )
        assertEquals("Dentist", event.title)
        assertEquals("Example Street 12, Berlin", event.location)
        assertEquals(Availability.BUSY, event.availability)
    }

    @Test
    fun `every form of alarm trigger`() {
        val event = read("nextcloud/alarms-forms.ics").single()

        // Understood: from the start, a day before, absolute, and at the start. Not shown: from the
        // end (they fall after the start of this one-hour event), after the start and broken ones.
        assertEquals(
            listOf(Reminder(15), Reminder(1440), Reminder(30), Reminder(0)),
            event.reminders
        )
    }

    @Test
    fun `alarms relative to the end count from the start of the event`() {
        val calendar = IcsParser.parse(
            "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:x\n" +
                "DTSTART:20261020T120000Z\nDTEND:20261020T150000Z\n" +
                "BEGIN:VALARM\nTRIGGER;RELATED=END:-PT3H30M\nEND:VALARM\n" +
                "BEGIN:VALARM\nTRIGGER;RELATED=END:-PT1H\nEND:VALARM\nEND:VEVENT\nEND:VCALENDAR\n"
        ).single()

        val event = VeventMapper.read(calendar, madrid).single()

        // 3.5 hours before the end is half an hour before the start; 1 hour before it is after.
        assertEquals(listOf(Reminder(30)), event.reminders)
    }

    @Test
    fun `RDATE with dates and periods, EXDATE lists`() {
        val daily = read("nextcloud/rdate-daily-count.ics").single()
        assertEquals(
            setOf(
                OccurrenceKey.Day(LocalDate.of(2026, 11, 5)),
                OccurrenceKey.Day(LocalDate.of(2026, 11, 7))
            ),
            daily.rDates
        )
        assertEquals(setOf(OccurrenceKey.Day(LocalDate.of(2026, 11, 8))), daily.exDates)

        val periods = read("nextcloud/rdate-period-utc.ics").single()
        assertEquals(
            setOf(moment("2026-11-10T08:00:00Z"), moment("2026-11-12T08:00:00Z")),
            periods.rDates
        )
        assertEquals(
            setOf(moment("2026-11-03T08:00:00Z"), moment("2026-11-04T08:00:00Z")),
            periods.exDates
        )
    }

    @Test
    fun `Outlook event in a Windows zone known only by name and VTIMEZONE`() {
        val event = read("outlook/windows-timezone-vtimezone-only.ics").single()

        assertEquals(
            timed("2026-10-22T12:00:00Z", "2026-10-22T13:30:00Z", ZoneId.of("Europe/Berlin")),
            event.time
        )
        assertEquals("jane.doe@example.com", event.organizer)
        assertEquals(
            listOf(
                Attendee("ana@example.com", "Example, Ana"),
                Attendee(
                    "bob@example.com",
                    "Bob Example",
                    AttendeeRole.OPTIONAL,
                    AttendeeStatus.ACCEPTED
                )
            ),
            event.attendees
        )
        assertEquals(Availability.TENTATIVE, event.availability)
        assertEquals("Quarterly planning", event.title)
        assertEquals("Quarterly planning.\n\nAgenda attached.\n", event.description)
        assertEquals("Conference Room 2", event.location)
        assertEquals(listOf(Reminder(15)), event.reminders)
    }

    @Test
    fun `Outlook cancellation with DURATION instead of DTEND`() {
        val event = read("outlook/cancelled-with-duration.ics").single()

        assertEquals(EventStatus.CANCELLED, event.status)
        assertEquals(
            timed("2026-10-23T09:00:00Z", "2026-10-23T10:30:00Z", ZoneOffset.UTC),
            event.time
        )
        assertEquals(Availability.FREE, event.availability)
        assertEquals(3, event.sequence)
    }

    @Test
    fun `a private zone uses the offset its VTIMEZONE declares and an undefined one is floating`() {
        val private = read("outlook/custom-vtimezone-fixed-offset.ics").single()
        val undefined = read("outlook/undefined-timezone.ics").single()

        assertEquals(
            timed("2026-10-26T04:30:00Z", "2026-10-26T05:30:00Z", ZoneOffset.ofHoursMinutes(5, 30)),
            private.time
        )
        // Read as the phone's zone (Madrid, +01:00 on this day); no end means zero length.
        assertEquals(
            timed("2026-10-26T09:00:00Z", "2026-10-26T09:00:00Z", madrid),
            undefined.time
        )
    }

    @Test
    fun `Apple floating time is read in the phone's zone`() {
        val event = read("apple/floating-and-structured-location.ics").single()

        assertEquals(
            timed("2026-10-09T16:00:00Z", "2026-10-09T17:00:00Z", madrid),
            event.time
        )
        assertEquals("Cafe Example\nMain Street 1\nSpringfield", event.location)
        // The 1976 trigger is Apple's placeholder for the default alarm, not a moment.
        assertEquals(listOf(Reminder(60)), event.reminders)
    }

    @Test
    fun `Apple attendees by principal with an EMAIL parameter, delegated and unaddressable ones`() {
        val event = read("apple/invitation-email-parameter.ics").single()

        assertEquals("carla@example.com", event.organizer)
        assertEquals(
            timed("2026-10-15T19:00:00Z", "2026-10-15T20:30:00Z", ZoneId.of("America/New_York")),
            event.time
        )
        assertEquals(
            listOf(
                Attendee(
                    "carla@example.com",
                    "Carla Example",
                    status = AttendeeStatus.ACCEPTED,
                    isOrganizer = true
                ),
                Attendee("ana@example.com", "Ana Example"),
                Attendee(
                    "dan@example.com",
                    "Dan Example",
                    AttendeeRole.OPTIONAL,
                    AttendeeStatus.DECLINED
                ),
                Attendee("eve@example.com", "Delegated Person")
            ),
            event.attendees
        )
    }

    @Test
    fun `Apple all-day yearly event with one date, and a UTC event`() {
        val halloween = read("apple/all-day-yearly.ics").single()
        val utc = read("apple/utc-timed.ics").single()

        assertEquals(
            EventTime.AllDay(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1)),
            halloween.time
        )
        assertEquals("FREQ=YEARLY", halloween.rrule)
        assertEquals(Availability.FREE, halloween.availability)
        assertEquals(
            timed("2026-11-02T23:00:00Z", "2026-11-03T00:30:00Z", ZoneOffset.UTC),
            utc.time
        )
    }

    @Test
    fun `non-ASCII text folded inside characters and escapes`() {
        val event = read("edge-cases/unicode-folded-crlf.ics").single()

        assertEquals(
            3,
            Regex("Cumpleaños de María José, ünïcödé 🎉 日本語のタイトル").findAll(event.title).count()
        )
        assertEquals(2, Regex("Línea muy larga").findAll(event.description.orEmpty()).count())
        assertEquals(
            EventTime.AllDay(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6)),
            event.time
        )
    }

    @Test
    fun `mixed line endings and a folded summary`() {
        val event = read("edge-cases/mixed-line-endings.ics").single()

        assertEquals("Mixedline endings", event.title)
        assertEquals(Instant.parse("2026-11-05T10:00:00Z"), event.time.startIn(ZoneOffset.UTC))
    }

    @Test
    fun `an unknown line, a missing END and a double-space fold`() {
        val folded = read("edge-cases/crlf-folded-alarm.ics").single()
        assertTrue(folded.title.startsWith("Call María, revisar el presupuesto; y enviar"))
        assertTrue(folded.title.endsWith("para que se pliegue a mitad"))
        assertEquals(
            timed("2026-10-05T07:30:00Z", "2026-10-05T08:30:00Z", madrid),
            folded.time
        )
        assertEquals(listOf(Reminder(15)), folded.reminders)

        val open = read("edge-cases/malformed-missing-end.ics").single()
        assertEquals("Without a final END", open.title)
        assertEquals(Instant.parse("2026-10-05T09:30:00Z"), open.time.startIn(ZoneOffset.UTC))
    }

    @Test
    fun `a VEVENT without a valid DTSTART is not readable`() {
        val calendar = IcsParser.parse(
            "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:a\nDTSTART:soon\nEND:VEVENT\n" +
                "BEGIN:VEVENT\nUID:b\nSUMMARY:No start\nEND:VEVENT\nEND:VCALENDAR\n"
        ).single()

        assertEquals(emptyList<VeventFields>(), VeventMapper.read(calendar, madrid))
    }

    @Test
    fun `odd end times are made valid`() {
        val calendar = IcsParser.parse(
            "BEGIN:VCALENDAR\n" +
                // All-day with a DTEND on the same day, and one with a date-time DTEND.
                "BEGIN:VEVENT\nUID:a\nDTSTART;VALUE=DATE:20261010\n" +
                "DTEND;VALUE=DATE:20261010\nEND:VEVENT\n" +
                "BEGIN:VEVENT\nUID:b\nDTSTART;VALUE=DATE:20261010\n" +
                "DTEND:20261012T000000\nEND:VEVENT\n" +
                "BEGIN:VEVENT\nUID:c\nDTSTART;VALUE=DATE:20261010\nDURATION:P2DT12H\nEND:VEVENT\n" +
                "BEGIN:VEVENT\nUID:d\nDTSTART;VALUE=DATE:20261010\nDURATION:P1W\nEND:VEVENT\n" +
                // A timed event that ends before it starts.
                "BEGIN:VEVENT\nUID:e\nDTSTART:20261010T100000Z\n" +
                "DTEND:20261010T090000Z\nEND:VEVENT\n" +
                "END:VCALENDAR\n"
        ).single()

        val times = VeventMapper.read(calendar, madrid).map { it.time }

        assertEquals(
            listOf(
                EventTime.AllDay(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11)),
                EventTime.AllDay(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 12)),
                EventTime.AllDay(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 13)),
                EventTime.AllDay(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 17)),
                timed("2026-10-10T10:00:00Z", "2026-10-10T10:00:00Z", ZoneOffset.UTC)
            ),
            times
        )
    }

    @Test
    fun `status, availability and organizer details`() {
        val calendar = IcsParser.parse(
            "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\nUID:a\nDTSTART:20261010T100000Z\n" +
                "STATUS:TENTATIVE\nSEQUENCE:x\nEND:VEVENT\n" +
                "BEGIN:VEVENT\nUID:b\nDTSTART:20261010T100000Z\nX-MICROSOFT-CDO-BUSYSTATUS:FREE\n" +
                "ORGANIZER:urn:uuid:no-address\nATTENDEE;EMAIL=nobody:urn:x\nEND:VEVENT\n" +
                "BEGIN:VEVENT\nUID:c\nDTSTART:20261010T100000Z\nTRANSP:TRANSPARENT\n" +
                "STATUS:unknown\nEND:VEVENT\n" +
                "END:VCALENDAR\n"
        ).single()

        val (tentative, free, transparent) = VeventMapper.read(calendar, madrid)

        assertEquals(EventStatus.TENTATIVE, tentative.status)
        assertEquals(Availability.TENTATIVE, tentative.availability)
        assertEquals(0, tentative.sequence)
        assertEquals(Availability.FREE, free.availability)
        assertNull(free.organizer)
        assertEquals(emptyList<Attendee>(), free.attendees)
        assertEquals(Availability.FREE, transparent.availability)
        assertEquals(EventStatus.CONFIRMED, transparent.status)
    }

    @Test
    fun `reminders of all-day events count from the start of the day`() {
        val calendar = IcsParser.parse(
            "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:x\nDTSTART;VALUE=DATE:20261010\n" +
                "BEGIN:VALARM\nACTION:DISPLAY\nTRIGGER:-PT9H\nEND:VALARM\n" +
                "BEGIN:VALARM\nACTION:DISPLAY\nTRIGGER;VALUE=DATE-TIME:20261009T150000\n" +
                "END:VALARM\n" +
                "BEGIN:VALARM\nACTION:DISPLAY\nTRIGGER;VALUE=DATE-TIME:garbage\nEND:VALARM\n" +
                "END:VEVENT\nEND:VCALENDAR\n"
        ).single()

        val event = VeventMapper.read(calendar, madrid).single()

        // 15:00 the day before, in the phone's zone, is nine hours before midnight.
        assertEquals(listOf(Reminder(540), Reminder(540)), event.reminders)
    }
}
