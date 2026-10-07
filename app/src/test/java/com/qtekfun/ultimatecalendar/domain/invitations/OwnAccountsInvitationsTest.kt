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
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The user has several accounts on the phone: an invitation to one of them is found anywhere. */
class OwnAccountsInvitationsTest {
    private val now = Instant.parse("2026-06-10T12:00:00Z")
    private val detector = InvitationDetector(Clock.fixed(now, ZoneOffset.UTC))
    private val a = "a@gmail.com"
    private val b = "b@gmail.com"
    private val c = "c@gmail.com"
    private val soon = EventTime.Timed(now.plusSeconds(3600), now.plusSeconds(7200), ZoneOffset.UTC)

    private fun calendar(
        id: Long,
        account: String,
        owner: String? = account,
        access: CalendarAccess = CalendarAccess.OWNER,
        type: String = "com.google"
    ) = CalendarInfo(
        id = CalendarId(id),
        account = CalendarAccount(account, type),
        displayName = "c$id",
        color = 0,
        access = access,
        ownerEmail = owner
    )

    private val calendars = listOf(calendar(1, a), calendar(2, b), calendar(3, c))

    private fun event(
        id: Long,
        calendar: Long,
        guests: List<Attendee>,
        uid: String? = "uid-1",
        time: EventTime = soon,
        organizer: String? = a,
        title: String = "Dinner"
    ) = Event(
        id = EventId(id),
        calendarId = CalendarId(calendar),
        title = title,
        time = time,
        organizer = organizer,
        attendees = guests,
        uid = uid
    )

    private fun organizer(email: String = a, status: AttendeeStatus = AttendeeStatus.ACCEPTED) =
        Attendee.of(email, status = status, isOrganizer = true)

    private fun guest(email: String, status: AttendeeStatus = AttendeeStatus.NEEDS_ACTION) =
        Attendee.of(email, status = status)

    private fun scan(vararg events: Event, aliases: Set<String> = emptySet()) =
        detector.scan(events.toList(), calendars, aliases)

    private fun key(calendar: Long, event: Long, address: String = "") =
        InvitationKey(CalendarId(calendar), EventId(event), address)

    @Test
    fun `an event of account A that invites my account B is an invitation for B`() {
        val result = scan(event(10, 1, listOf(organizer(), guest(b))))

        val pending = result.pending.single()
        assertEquals(key(1, 10, b), pending.key)
        assertEquals(b, pending.account)
        assertTrue(pending.key.isForeign)
        // The organizer's own row never counts, and the event is accounted for per address.
        assertEquals(AttendeeStatus.ACCEPTED, result.states.getValue(key(1, 10)).myStatus)
        assertEquals(AttendeeStatus.NEEDS_ACTION, result.states.getValue(key(1, 10, b)).myStatus)
    }

    @Test
    fun `the organizer row of my own account is not an invitation even when it is unanswered`() {
        val result = scan(event(10, 1, listOf(organizer(status = AttendeeStatus.NEEDS_ACTION))))

        assertEquals(emptyList<Invitation>(), result.pending)
    }

    @Test
    fun `an event that invites two of my accounts is two invitations`() {
        val result = scan(event(10, 1, listOf(organizer(), guest(b), guest(c))))

        assertEquals(
            listOf(key(1, 10, b), key(1, 10, c)),
            result.pending.map { it.key }.sortedBy { it.address }
        )
    }

    @Test
    fun `when B's own calendar has its copy, that copy is the invitation and A adds none`() {
        val result = scan(
            event(10, 1, listOf(organizer(), guest(b))),
            event(20, 2, listOf(organizer(), guest(b)))
        )

        val pending = result.pending.single()
        assertEquals(key(2, 20), pending.key)
        assertEquals(b, pending.account)
        assertFalse(pending.key.isForeign)
        assertEquals(AttendeeStatus.NEEDS_ACTION, result.states.getValue(key(1, 10, b)).myStatus)
    }

    @Test
    fun `an answer in B's copy stops the invitation for B and A's copy does not bring it back`() {
        val result = scan(
            event(10, 1, listOf(organizer(), guest(b))),
            event(20, 2, listOf(organizer(), guest(b, AttendeeStatus.DECLINED)))
        )

        assertEquals(emptyList<Invitation>(), result.pending)
        assertEquals(AttendeeStatus.DECLINED, result.states.getValue(key(1, 10, b)).myStatus)
    }

    @Test
    fun `answering as B leaves the invitation for C pending`() {
        val result = scan(
            event(10, 1, listOf(organizer(), guest(b), guest(c))),
            event(20, 2, listOf(organizer(), guest(b, AttendeeStatus.TENTATIVE), guest(c)))
        )

        assertEquals(listOf(key(1, 10, c)), result.pending.map { it.key })
    }

    @Test
    fun `a copy that does not list B counts as answered`() {
        val result = scan(
            event(10, 1, listOf(organizer(), guest(b))),
            event(20, 2, listOf(organizer()))
        )

        assertEquals(emptyList<Invitation>(), result.pending)
        assertEquals(AttendeeStatus.ACCEPTED, result.states.getValue(key(1, 10, b)).myStatus)
    }

    @Test
    fun `an event with another start is not a copy`() {
        val later = EventTime.Timed(now.plusSeconds(9000), now.plusSeconds(12_000), ZoneOffset.UTC)
        val result = scan(
            event(10, 1, listOf(organizer(), guest(b))),
            event(20, 2, listOf(organizer(), guest(b)), time = later)
        )

        assertEquals(setOf(key(1, 10, b), key(2, 20)), result.pending.map { it.key }.toSet())
    }

    @Test
    fun `events without a uid are copies when title, start and organizer agree`() {
        val same = scan(
            event(10, 1, listOf(organizer(), guest(b)), uid = null),
            event(20, 2, listOf(organizer(), guest(b)), uid = null)
        )
        val other = scan(
            event(10, 1, listOf(organizer(), guest(b)), uid = null),
            event(20, 2, listOf(organizer(), guest(b)), uid = null, title = "Other")
        )

        assertEquals(listOf(key(2, 20)), same.pending.map { it.key })
        assertEquals(2, other.pending.size)
    }

    @Test
    fun `two foreign copies of one invitation are one, the organizer's preferred`() {
        val organised = scan(
            event(30, 3, listOf(organizer(), guest(b))),
            event(10, 1, listOf(organizer(), guest(b)))
        )
        // Neither organised by its own account: the lowest calendar, then the lowest event.
        val neither = scan(
            event(30, 3, listOf(organizer("x@x.org"), guest(b)), organizer = "x@x.org"),
            event(11, 1, listOf(organizer("x@x.org"), guest(b)), organizer = "x@x.org"),
            event(10, 1, listOf(organizer("x@x.org"), guest(b)), organizer = null)
        )

        assertEquals(listOf(key(1, 10, b)), organised.pending.map { it.key })
        assertEquals(listOf(key(1, 10, b)), neither.pending.map { it.key })
        // The copy that was not chosen is accounted for, not cancelled.
        assertEquals(AttendeeStatus.NEEDS_ACTION, neither.states.getValue(key(3, 30, b)).myStatus)
    }

    @Test
    fun `the copy of the account that organises wins over a lower calendar`() {
        val result = scan(
            event(10, 1, listOf(organizer(c), guest(b)), organizer = c),
            event(30, 3, listOf(organizer(c), guest(b)), organizer = c)
        )

        assertEquals(key(3, 30, b), result.pending.single().key)
    }

    @Test
    fun `a past or answered foreign invitation is not pending but is accounted for`() {
        val past = EventTime.Timed(now.minusSeconds(7200), now.minusSeconds(3600), ZoneOffset.UTC)
        val result = scan(
            event(10, 1, listOf(organizer(), guest(b)), time = past),
            event(11, 1, listOf(organizer(), guest(b, AttendeeStatus.ACCEPTED)), uid = "other")
        )

        assertEquals(emptyList<Invitation>(), result.pending)
        assertFalse(result.states.getValue(key(1, 10, b)).isFuture)
        assertEquals(AttendeeStatus.ACCEPTED, result.states.getValue(key(1, 11, b)).myStatus)
    }

    @Test
    fun `my aliases and the owner of the event's calendar stay local, not foreign`() {
        val result = scan(
            event(10, 1, listOf(organizer(c), guest(a), guest("me@work.org"))),
            aliases = setOf("ME@work.org")
        )

        // The alias is a local address everywhere; A's own row is the local invitation.
        assertEquals(listOf(key(1, 10)), result.pending.map { it.key })
    }

    @Test
    fun `an event of a calendar the phone does not list is judged by the addresses alone`() {
        val result = scan(event(10, 9, listOf(guest(a))))

        assertNull(result.states.getValue(key(9, 10)).myStatus)
        assertEquals(listOf(key(9, 10, a)), result.pending.map { it.key })
    }

    @Test
    fun `group, holiday and read-only calendars do not make anyone me`() {
        val others = calendars + listOf(
            calendar(7, "acc7", owner = "team@group.calendar.google.com"),
            calendar(8, "acc8", owner = "es@group.v.calendar.google.com"),
            calendar(9, "acc9", owner = "#holiday@group.v.calendar.google.com"),
            calendar(10, "acc10", owner = "room@resource.calendar.google.com"),
            calendar(11, "acc11", owner = "boss@gmail.com", access = CalendarAccess.READ)
        )
        val guests = listOf(
            organizer(),
            guest("boss@gmail.com"),
            guest("team@group.calendar.google.com"),
            guest("room@resource.calendar.google.com")
        )
        val result = detector.scan(listOf(event(10, 1, guests)), others, emptySet())

        assertEquals(emptyList<Invitation>(), result.pending)
    }

    @Test
    fun `a single account does not label its invitations`() {
        val result = detector.scan(
            listOf(event(10, 1, listOf(organizer(b), guest(a)), organizer = b)),
            listOf(calendars.first()),
            emptySet()
        )

        assertNull(result.pending.single().account)
    }

    @Test
    fun `an invitation that moves from A's copy to B's copy replaces its notification`() {
        val before = scan(event(10, 1, listOf(organizer(), guest(b))))
        val after = scan(
            event(10, 1, listOf(organizer(), guest(b))),
            event(20, 2, listOf(organizer(), guest(b)))
        )

        val changes = detector.diff(before.pending, after)

        assertEquals(listOf(key(2, 20)), changes.new.map { it.key })
        assertEquals(listOf(key(1, 10, b)), changes.answeredElsewhere.map { it.key })
    }

    @Test
    fun `a foreign invitation is cancelled when B is no longer invited, settled when answered`() {
        val before = scan(event(10, 1, listOf(organizer(), guest(b))))
        val removed = scan(event(10, 1, listOf(organizer())))
        val answered = scan(
            event(10, 1, listOf(organizer(), guest(b))),
            event(20, 2, listOf(organizer(), guest(b, AttendeeStatus.ACCEPTED)))
        )

        assertEquals(before.pending, detector.diff(before.pending, removed).cancelled)
        assertEquals(before.pending, detector.diff(before.pending, answered).answeredElsewhere)
        assertEquals(emptyList<Invitation>(), detector.diff(before.pending, answered).cancelled)
    }
}
