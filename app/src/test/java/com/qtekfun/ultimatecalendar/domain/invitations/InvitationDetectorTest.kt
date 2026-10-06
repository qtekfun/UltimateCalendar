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
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvitationDetectorTest {
    private val now = Instant.parse("2026-06-10T12:00:00Z")
    private val utc = ZoneOffset.UTC
    private val detector = detector(now, utc)
    private val cal = CalendarId(1)
    private val me = "me@example.com"

    private fun detector(at: Instant, zone: ZoneId) = InvitationDetector(Clock.fixed(at, zone))

    private fun calendar(id: Long = 1, owner: String? = me, visible: Boolean = true) = CalendarInfo(
        id = CalendarId(id),
        account = CalendarAccount("a", "type"),
        displayName = "c$id",
        color = 0,
        access = CalendarAccess.OWNER,
        visible = visible,
        ownerEmail = owner
    )

    private fun timed(start: Instant, zone: ZoneId = utc) =
        EventTime.Timed(start, start.plusSeconds(3600), zone)

    private fun event(
        id: Long = 10,
        calendar: Long = 1,
        time: EventTime = timed(now.plusSeconds(3600)),
        attendees: List<Attendee> = listOf(Attendee.of(me)),
        location: String? = null,
        rrule: String? = null
    ) = Event(
        id = EventId(id),
        calendarId = CalendarId(calendar),
        title = "t$id",
        time = time,
        location = location,
        rrule = rrule,
        organizer = "boss@example.com",
        attendees = attendees
    )

    private fun scan(
        vararg events: Event,
        aliases: Set<String> = emptySet(),
        calendars: List<CalendarInfo> = listOf(calendar())
    ) = detector.scan(events.toList(), calendars, aliases)

    private fun pendingIds(scan: InvitationScan) = scan.pending.map { it.key.eventId.value }

    @Test
    fun `future event with my unanswered attendee is pending with its data`() {
        val result = scan(event(location = "Room"))
        assertEquals(
            listOf(
                Invitation(
                    InvitationKey(cal, EventId(10)),
                    "t10",
                    timed(now.plusSeconds(3600)),
                    "Room",
                    "boss@example.com"
                )
            ),
            result.pending
        )
    }

    @Test
    fun `answered events are not pending`() {
        for (status in AttendeeStatus.entries.filterNot { it.isPending }) {
            val result = scan(event(attendees = listOf(Attendee.of(me, status = status))))
            assertTrue(result.pending.isEmpty(), "$status")
        }
    }

    @Test
    fun `hidden calendars count too`() {
        val result = scan(event(), calendars = listOf(calendar(visible = false)))
        assertEquals(listOf(10L), pendingIds(result))
    }

    @Test
    fun `me is matched without case through the calendar owner`() {
        val result = scan(event(attendees = listOf(Attendee.of("ME@Example.com"))))
        assertEquals(listOf(10L), pendingIds(result))
    }

    @Test
    fun `me is matched through an alias`() {
        val attendees = listOf(Attendee.of("alias@example.org"))
        val withAlias = scan(event(attendees = attendees), aliases = setOf("Alias@Example.org"))
        assertEquals(listOf(10L), pendingIds(withAlias))
        assertTrue(scan(event(attendees = attendees)).pending.isEmpty())
    }

    @Test
    fun `the owner of another calendar is not me`() {
        val other = calendar(id = 2, owner = "other@example.com")
        val result = scan(event(calendar = 1), calendars = listOf(other))
        assertTrue(result.pending.isEmpty())
    }

    @Test
    fun `a calendar without owner or unknown relies on aliases only`() {
        val noOwner = calendar(owner = null)
        assertTrue(scan(event(), calendars = listOf(noOwner)).pending.isEmpty())
        assertEquals(
            listOf(10L),
            pendingIds(scan(event(), aliases = setOf(me), calendars = listOf(noOwner)))
        )
        assertEquals(
            listOf(10L),
            pendingIds(scan(event(), aliases = setOf(me), calendars = emptyList()))
        )
    }

    @Test
    fun `events without me as attendee are not pending`() {
        assertTrue(scan(event(attendees = emptyList())).pending.isEmpty())
        assertTrue(scan(event(attendees = listOf(Attendee.of("x@example.com")))).pending.isEmpty())
    }

    @Test
    fun `pending invitations are sorted by start`() {
        val late = event(id = 1, time = timed(now.plusSeconds(7200)))
        val early = event(id = 2, time = timed(now.plusSeconds(60)))
        val allDay = event(
            id = 3,
            time = EventTime.AllDay(LocalDate.parse("2026-06-11"), LocalDate.parse("2026-06-12"))
        )
        assertEquals(listOf(2L, 1L, 3L), pendingIds(scan(late, allDay, early)))
    }

    @Test
    fun `started and past events are not future but are still known`() {
        val started = event(id = 1, time = timed(now))
        val past = event(id = 2, time = timed(now.minusSeconds(1)))
        val result = scan(started, past)
        assertTrue(result.pending.isEmpty())
        assertEquals(
            mapOf(
                InvitationKey(cal, EventId(1)) to EventState(false, AttendeeStatus.NEEDS_ACTION),
                InvitationKey(cal, EventId(2)) to EventState(false, AttendeeStatus.NEEDS_ACTION)
            ),
            result.states
        )
    }

    @Test
    fun `the future boundary follows the instant across the spring DST jump`() {
        // Madrid jumps from 02:00 to 03:00 local at 01:00Z on 2026-03-29.
        val madrid = ZoneId.of("Europe/Madrid")
        val at = Instant.parse("2026-03-29T00:30:00Z")
        val d = detector(at, madrid)
        val just = event(id = 1, time = timed(Instant.parse("2026-03-29T00:30:01Z"), madrid))
        val equal = event(id = 2, time = timed(at, madrid))
        val result = d.scan(listOf(just, equal), listOf(calendar()), emptySet())
        assertEquals(listOf(1L), pendingIds(result))
    }

    @Test
    fun `an all-day event is future depending on the clock zone`() {
        val day = EventTime.AllDay(LocalDate.parse("2026-06-11"), LocalDate.parse("2026-06-12"))
        val e = event(time = day)
        // 12:00Z is already June 11 00:00 in Auckland (UTC+12), still June 10 in UTC.
        val auckland = detector(
            now,
            ZoneId.of("Pacific/Auckland")
        ).scan(listOf(e), listOf(calendar()), emptySet())
        val inUtc = detector(now, utc).scan(listOf(e), listOf(calendar()), emptySet())
        val newYork = detector(
            now,
            ZoneId.of("America/New_York")
        ).scan(listOf(e), listOf(calendar()), emptySet())
        assertTrue(auckland.pending.isEmpty())
        assertEquals(listOf(10L), pendingIds(inUtc))
        assertEquals(listOf(10L), pendingIds(newYork))
    }

    @Test
    fun `recurring series started in the past are future unless they ended`() {
        val past = timed(now.minusSeconds(86_400 * 30))

        fun series(rrule: String) = scan(event(time = past, rrule = rrule)).pending.isNotEmpty()
        assertTrue(series("FREQ=WEEKLY"))
        assertTrue(series("FREQ=WEEKLY;COUNT=10"))
        assertTrue(series("FREQ=WEEKLY;UNTIL=20260610"))
        assertTrue(series("not a rule"))
        assertFalse(series("FREQ=WEEKLY;UNTIL=20260609"))
    }

    @Test
    fun `the until date is compared in the clock zone`() {
        val past = timed(now.minusSeconds(86_400 * 30))
        // In Auckland it is already June 11, so a series ending June 10 is over.
        val d = detector(now, ZoneId.of("Pacific/Auckland"))
        val e = event(time = past, rrule = "FREQ=DAILY;UNTIL=20260610")
        assertTrue(d.scan(listOf(e), listOf(calendar()), emptySet()).pending.isEmpty())
    }

    @Test
    fun `a non recurring past event is never future`() {
        assertTrue(scan(event(time = timed(now.minusSeconds(5)))).pending.isEmpty())
    }

    private fun inv(
        id: Long = 10,
        time: EventTime = timed(now.plusSeconds(3600)),
        location: String? = null
    ) = Invitation(InvitationKey(cal, EventId(id)), "t$id", time, location, null)

    @Test
    fun `first run reports everything as new and nothing else`() {
        val changes = detector.diff(emptyList(), scan(event(id = 1), event(id = 2)))
        assertEquals(listOf(1L, 2L), changes.new.map { it.key.eventId.value })
        assertTrue(
            changes.changed.isEmpty() && changes.cancelled.isEmpty() &&
                changes.answeredElsewhere.isEmpty()
        )
        assertFalse(changes.isEmpty)
    }

    @Test
    fun `changes are empty only when every list is empty`() {
        val i = inv()
        val none = InvitationChanges(emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(none.isEmpty)
        assertFalse(none.copy(new = listOf(i)).isEmpty)
        assertFalse(none.copy(changed = listOf(InvitationChange(i, i))).isEmpty)
        assertFalse(none.copy(cancelled = listOf(i)).isEmpty)
        assertFalse(none.copy(answeredElsewhere = listOf(i)).isEmpty)
    }

    @Test
    fun `an unchanged invitation produces no differences`() {
        val current = scan(event(location = "Room"))
        assertTrue(detector.diff(current.pending, current).isEmpty)
    }

    @Test
    fun `a new time or zone is a change`() {
        val previous = inv()
        val moved = scan(event(time = timed(now.plusSeconds(7200))))
        val changes = detector.diff(listOf(previous), moved)
        assertEquals(listOf(InvitationChange(previous, moved.pending.single())), changes.changed)
        assertTrue(changes.new.isEmpty())

        val zone = scan(event(time = timed(now.plusSeconds(3600), ZoneId.of("Asia/Tokyo"))))
        assertEquals(1, detector.diff(listOf(previous), zone).changed.size)
    }

    @Test
    fun `a new place is a change but blank equals none`() {
        assertEquals(
            1,
            detector.diff(listOf(inv(location = "A")), scan(event(location = "B"))).changed.size
        )
        assertEquals(1, detector.diff(listOf(inv()), scan(event(location = "B"))).changed.size)
        assertEquals(1, detector.diff(listOf(inv(location = "A")), scan(event())).changed.size)
        assertTrue(detector.diff(listOf(inv(location = "  ")), scan(event())).isEmpty)
        assertTrue(
            detector.diff(listOf(inv(location = "A")), scan(event(location = " A "))).isEmpty
        )
    }

    @Test
    fun `a deleted event is cancelled`() {
        val previous = inv()
        val changes = detector.diff(listOf(previous), scan())
        assertEquals(listOf(previous), changes.cancelled)
        assertTrue(changes.answeredElsewhere.isEmpty())
    }

    @Test
    fun `being removed from the attendees is a cancellation`() {
        val previous = inv()
        val changes = detector.diff(
            listOf(previous),
            scan(event(attendees = listOf(Attendee.of("x@example.com"))))
        )
        assertEquals(listOf(previous), changes.cancelled)
    }

    @Test
    fun `answering elsewhere is reported with any answer`() {
        val previous = inv()
        for (status in AttendeeStatus.entries.filterNot { it.isPending }) {
            val current = scan(event(attendees = listOf(Attendee.of(me, status = status))))
            val changes = detector.diff(listOf(previous), current)
            assertEquals(listOf(previous), changes.answeredElsewhere, "$status")
            assertTrue(changes.cancelled.isEmpty())
        }
    }

    @Test
    fun `an invitation that simply passed is not reported`() {
        val passed = scan(event(time = timed(now.minusSeconds(60))))
        assertTrue(detector.diff(listOf(inv()), passed).isEmpty)
    }

    @Test
    fun `all kinds of differences are reported together`() {
        val changed = inv(id = 1)
        val gone = inv(id = 2)
        val answered = inv(id = 3)
        val current = scan(
            event(id = 1, location = "New"),
            event(id = 3, attendees = listOf(Attendee.of(me, status = AttendeeStatus.ACCEPTED))),
            event(id = 4)
        )
        val changes = detector.diff(listOf(changed, gone, answered), current)
        assertEquals(listOf(4L), changes.new.map { it.key.eventId.value })
        assertEquals(listOf(1L), changes.changed.map { it.previous.key.eventId.value })
        assertEquals(listOf(gone), changes.cancelled)
        assertEquals(listOf(answered), changes.answeredElsewhere)
    }
}
