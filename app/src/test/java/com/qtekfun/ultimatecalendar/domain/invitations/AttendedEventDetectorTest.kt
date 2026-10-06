// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AttendedEventDetectorTest {
    private val now = Instant.parse("2026-06-10T12:00:00Z")
    private val utc = ZoneOffset.UTC
    private val detector = AttendedEventDetector(Clock.fixed(now, utc))
    private val me = "me@example.com"
    private val boss = "boss@example.com"

    private fun calendar(id: Long = 1, access: CalendarAccess = CalendarAccess.OWNER) =
        CalendarInfo(
            id = CalendarId(id),
            account = CalendarAccount("a", "type"),
            displayName = "c$id",
            color = 0,
            access = access,
            ownerEmail = me
        )

    private fun timed(start: Instant, zone: ZoneId = utc) =
        EventTime.Timed(start, start.plusSeconds(3600), zone)

    private val tomorrow = now.plusSeconds(86_400)

    private fun going(status: AttendeeStatus = AttendeeStatus.ACCEPTED) = listOf(
        Attendee.of(boss, isOrganizer = true, status = AttendeeStatus.ACCEPTED),
        Attendee.of(me, status = status)
    )

    private fun event(
        id: Long = 10,
        calendar: Long = 1,
        time: EventTime = timed(tomorrow),
        attendees: List<Attendee> = going(),
        location: String? = null,
        rrule: String? = null,
        organizer: String? = boss,
        title: String = "t$id"
    ) = Event(
        id = EventId(id),
        calendarId = CalendarId(calendar),
        title = title,
        time = time,
        location = location,
        rrule = rrule,
        organizer = organizer,
        attendees = attendees
    )

    private fun instance(event: Event, time: EventTime = event.time) = EventInstance(
        eventId = event.id,
        calendarId = event.calendarId,
        title = event.title,
        time = time,
        isRecurring = event.isRecurring
    )

    private fun scan(
        vararg events: Event,
        instances: List<EventInstance> = events.map { instance(it) },
        calendars: List<CalendarInfo> = listOf(calendar(1), calendar(2)),
        aliases: Set<String> = emptySet()
    ) = detector.scan(events.toList(), instances, calendars, aliases)

    private fun key(id: Long = 10, calendar: Long = 1) =
        InvitationKey(CalendarId(calendar), EventId(id))

    private fun recordOf(event: Event) = scan(event).tracked.single().record

    private fun tracked(scan: AttendedScan) = scan.tracked.map { it.record.key.eventId.value }

    @Test
    fun `an accepted or maybe event with other attendees is followed with its data`() {
        val accepted = scan(event(10, location = "Room"))
        val maybe = scan(event(11, attendees = going(AttendeeStatus.TENTATIVE)))

        assertEquals(
            AttendedEvent(key(), "t10", timed(tomorrow), AttendedEvent.placeHash("Room")),
            accepted.tracked.single().record
        )
        assertEquals(
            Invitation(key(), "t10", timed(tomorrow), "Room", boss),
            accepted.tracked.single().shown
        )
        assertEquals(listOf(11L), tracked(maybe))
        assertEquals(setOf(key()), accepted.seen)
        assertEquals(setOf(CalendarId(1), CalendarId(2)), accepted.calendars)
    }

    @Test
    fun `pending, declined, past, lonely, own, foreign and read-only events are not followed`() {
        val pending = event(1, attendees = going(AttendeeStatus.NEEDS_ACTION))
        val declined = event(2, attendees = going(AttendeeStatus.DECLINED))
        val past = event(3, time = timed(now.minusSeconds(60)))
        val alone = event(4, attendees = listOf(Attendee.of(me, status = AttendeeStatus.ACCEPTED)))
        val mine = event(
            5,
            attendees = listOf(
                Attendee.of(me, isOrganizer = true, status = AttendeeStatus.ACCEPTED),
                Attendee.of(boss, status = AttendeeStatus.ACCEPTED)
            ),
            organizer = me
        )
        val notMe = event(6, attendees = listOf(Attendee.of(boss), Attendee.of("x@example.com")))
        val readOnly = event(7, calendar = 2)
        val unknownCalendar = event(8, calendar = 9)
        val organizerByAddress = event(
            9,
            attendees = listOf(
                Attendee.of(me, status = AttendeeStatus.ACCEPTED),
                Attendee.of(boss, status = AttendeeStatus.ACCEPTED)
            ),
            organizer = " ME@example.com "
        )

        val result = scan(
            pending,
            declined,
            past,
            alone,
            mine,
            notMe,
            readOnly,
            unknownCalendar,
            organizerByAddress,
            calendars = listOf(calendar(1), calendar(2, CalendarAccess.READ)),
            instances = emptyList()
        )

        assertEquals(emptyList<Long>(), tracked(result))
        assertEquals(9, result.seen.size)
    }

    @Test
    fun `a calendar where only answering is allowed still counts, a read one does not`() {
        val respond = scan(event(), calendars = listOf(calendar(1, CalendarAccess.RESPOND)))
        val read = scan(event(), calendars = listOf(calendar(1, CalendarAccess.READ)))

        assertEquals(listOf(10L), tracked(respond))
        assertEquals(emptyList<Long>(), tracked(read))
    }

    @Test
    fun `an alias identifies the user and an organizer other than the user is fine`() {
        val byAlias = event(
            attendees = listOf(
                Attendee.of(boss, status = AttendeeStatus.ACCEPTED),
                Attendee.of("alias@example.com", status = AttendeeStatus.ACCEPTED)
            ),
            organizer = null
        )

        assertEquals(listOf(10L), tracked(scan(byAlias, aliases = setOf("Alias@example.com"))))
        assertEquals(emptyList<Long>(), tracked(scan(byAlias)))
    }

    @Test
    fun `followed events come soonest first`() {
        val later = event(1, time = timed(tomorrow.plusSeconds(7200)))
        val sooner = event(2, time = timed(tomorrow))

        assertEquals(listOf(2L, 1L), tracked(scan(later, sooner)))
    }

    @Test
    fun `a series follows its next occurrence, not its first`() {
        val series = event(time = timed(now.minusSeconds(86_400 * 30)), rrule = "FREQ=WEEKLY")
        val occurrences = listOf(
            instance(series, timed(now.minusSeconds(3600))),
            instance(series, timed(tomorrow.plusSeconds(86_400 * 7))),
            instance(series, timed(tomorrow))
        )

        val result = scan(series, instances = occurrences)

        assertEquals(timed(tomorrow), result.tracked.single().record.time)
    }

    @Test
    fun `a series with no upcoming occurrence is not followed, a lone event is`() {
        val ended = event(1, time = timed(now.minusSeconds(86_400)), rrule = "FREQ=DAILY;COUNT=1")
        val recurringUnseen = event(2, rrule = "FREQ=WEEKLY")
        val plain = event(3)

        val result = scan(ended, recurringUnseen, plain, instances = emptyList())

        assertEquals(listOf(3L), tracked(result))
    }

    @Test
    fun `the first run follows silently and an unchanged event is quiet`() {
        val current = scan(event())

        val first = detector.diff(emptyList(), current)
        val same = detector.diff(current.tracked.map { it.record }, current)

        assertTrue(first.isEmpty)
        assertTrue(same.isEmpty)
    }

    @Test
    fun `a moved event is reported with its old and new data`() {
        val before = recordOf(event(location = "Room"))
        val later = timed(tomorrow.plusSeconds(7200))

        val changes = detector.diff(listOf(before), scan(event(time = later, location = "Room")))

        val change = changes.changed.single()
        assertEquals(timed(tomorrow), change.previous.time)
        assertEquals(later, change.current.time)
        assertEquals("Room", change.current.location)
        assertTrue(changes.cancelled.isEmpty())
        assertTrue(changes.dropped.isEmpty())
        assertFalse(changes.isEmpty)
    }

    @Test
    fun `a new place, even with a hash only, a new zone and a new length each count as a change`() {
        val before = recordOf(event(location = "Room"))
        val other = detector.diff(listOf(before), scan(event(location = "Hall")))
        val removed = detector.diff(listOf(before), scan(event(location = null)))
        val blanksOnly = detector.diff(listOf(before), scan(event(location = "  Room ")))
        val zone = detector.diff(
            listOf(before),
            scan(event(time = timed(tomorrow, ZoneId.of("Europe/Madrid")), location = "Room"))
        )
        val longer = detector.diff(
            listOf(before),
            scan(
                event(
                    time = EventTime.Timed(tomorrow, tomorrow.plusSeconds(7200), utc),
                    location = "Room"
                )
            )
        )

        assertEquals(1, other.changed.size)
        assertEquals(1, removed.changed.size)
        assertTrue(blanksOnly.isEmpty)
        assertEquals(1, zone.changed.size)
        assertEquals(1, longer.changed.size)
    }

    @Test
    fun `an all-day event moving to another day is a change and the same days are not`() {
        val days = EventTime.AllDay(LocalDate.of(2026, 6, 20), LocalDate.of(2026, 6, 21))
        val moved = EventTime.AllDay(LocalDate.of(2026, 6, 22), LocalDate.of(2026, 6, 23))
        val before = recordOf(event(time = days))

        assertTrue(detector.diff(listOf(before), scan(event(time = days))).isEmpty)
        assertEquals(1, detector.diff(listOf(before), scan(event(time = moved))).changed.size)
    }

    @Test
    fun `the instant decides across a change of offset, not the wall clock`() {
        val madrid = ZoneId.of("Europe/Madrid")
        // Clocks go forward in Madrid on 2026-03-29; the instant is what counts, not the wall time.
        val march = Clock.fixed(Instant.parse("2026-03-20T00:00:00Z"), madrid)
        val dst = AttendedEventDetector(march)
        val day = Instant.parse("2026-03-29T07:00:00Z")
        val sameInstant = EventTime.Timed(day, day.plusSeconds(3600), madrid)
        val sameWallClock = EventTime.Timed(day.minusSeconds(3600), day, madrid)
        val owner = listOf(calendar())
        fun scanOf(time: EventTime) = event(time = time).let {
            dst.scan(listOf(it), listOf(instance(it)), owner, emptySet())
        }
        val before = scanOf(sameInstant).tracked.single().record

        assertTrue(dst.diff(listOf(before), scanOf(sameInstant)).isEmpty)
        assertEquals(1, dst.diff(listOf(before), scanOf(sameWallClock)).changed.size)
    }

    @Test
    fun `a moved occurrence of a series is a change, the series advancing is not`() {
        val series = event(time = timed(now.minusSeconds(86_400 * 30)), rrule = "FREQ=WEEKLY")
        val followed = AttendedEvent(
            key(),
            "t10",
            timed(tomorrow),
            AttendedEvent.placeHash(null)
        )
        val movedOccurrence =
            scan(series, instances = listOf(instance(series, timed(tomorrow.plusSeconds(3600)))))
        val unchanged = scan(series, instances = listOf(instance(series, timed(tomorrow))))
        // The followed occurrence has started: the next week's is the new reference.
        val advanced = followed.copy(time = timed(now.minusSeconds(60)))
        val nextWeek = scan(series, instances = listOf(instance(series, timed(tomorrow))))

        assertEquals(1, detector.diff(listOf(followed), movedOccurrence).changed.size)
        assertTrue(detector.diff(listOf(followed), unchanged).isEmpty)
        assertTrue(detector.diff(listOf(advanced), nextWeek).isEmpty)
    }

    @Test
    fun `an event moved to the past is dropped silently and so are the ones declined or left`() {
        val before = listOf(
            recordOf(event(1)),
            recordOf(event(2)),
            recordOf(event(3)),
            recordOf(event(4, calendar = 2))
        )
        val moved = event(1, time = timed(now.minusSeconds(3600)))
        val declined = event(2, attendees = going(AttendeeStatus.DECLINED))
        val left = event(3, attendees = listOf(Attendee.of(boss), Attendee.of("x@example.com")))
        val pendingAgain = event(4, calendar = 2, attendees = going(AttendeeStatus.NEEDS_ACTION))

        val changes = detector.diff(
            before,
            scan(moved, declined, left, pendingAgain, calendars = listOf(calendar(1), calendar(2)))
        )

        assertEquals(listOf(key(1), key(2), key(3), key(4, 2)), changes.dropped)
        assertTrue(changes.changed.isEmpty())
        assertTrue(changes.cancelled.isEmpty())
        assertFalse(changes.isEmpty)
    }

    @Test
    fun `an event gone from the source is cancelled once and then forgotten`() {
        val before = recordOf(event(title = "Planning", location = "Room"))
        val gone = scan(calendars = listOf(calendar()))

        val changes = detector.diff(listOf(before), gone)
        val after = detector.diff(emptyList(), gone)

        assertEquals(
            listOf(Invitation(key(), "Planning", timed(tomorrow))),
            changes.cancelled
        )
        assertTrue(changes.dropped.isEmpty())
        assertTrue(after.isEmpty)
    }

    @Test
    fun `an event of a calendar that vanished is dropped, not cancelled`() {
        val before = recordOf(event())

        val changes = detector.diff(listOf(before), scan(calendars = listOf(calendar(2))))

        assertTrue(changes.cancelled.isEmpty())
        assertEquals(listOf(key()), changes.dropped)
    }

    @Test
    fun `a change made by this app is not reported, nor is its deletion`() {
        val mine = recordOf(event()).copy(ownEdit = true)
        val moved = scan(event(time = timed(tomorrow.plusSeconds(7200))))
        val gone = scan()

        assertTrue(detector.diff(listOf(mine), moved).isEmpty)
        val deleted = detector.diff(listOf(mine), gone)
        assertTrue(deleted.cancelled.isEmpty())
        assertEquals(listOf(key()), deleted.dropped)
    }

    @Test
    fun `a change reported once is not reported again after the record is replaced`() {
        val before = recordOf(event())
        val moved = scan(event(time = timed(tomorrow.plusSeconds(7200))))

        val first = detector.diff(listOf(before), moved)
        val second = detector.diff(moved.tracked.map { it.record }, moved)

        assertEquals(1, first.changed.size)
        assertTrue(second.isEmpty)
    }

    @Test
    fun `the place is only a short hash that tells places apart`() {
        assertEquals("", AttendedEvent.placeHash(null))
        assertEquals("", AttendedEvent.placeHash("   "))
        assertEquals(16, AttendedEvent.placeHash("Room 1").length)
        assertEquals(AttendedEvent.placeHash("Room 1"), AttendedEvent.placeHash(" Room 1 "))
        assertNotEquals(AttendedEvent.placeHash("Room 1"), AttendedEvent.placeHash("Room 2"))
        assertFalse(AttendedEvent.placeHash("Room 1").contains("Room"))
    }

    @Test
    fun `each kind of change alone makes the changes not empty`() {
        val invitation = Invitation(key(), "t", timed(tomorrow))

        assertTrue(AttendedChanges(emptyList(), emptyList(), emptyList()).isEmpty)
        assertFalse(
            AttendedChanges(
                listOf(InvitationChange(invitation, invitation)),
                emptyList(),
                emptyList()
            ).isEmpty
        )
        assertFalse(AttendedChanges(emptyList(), listOf(invitation), emptyList()).isEmpty)
        assertFalse(AttendedChanges(emptyList(), emptyList(), listOf(key())).isEmpty)
    }
}
