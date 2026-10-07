// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.now
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The invitation check with several accounts on the phone, the author's own case. */
class InvitationCheckerAccountsTest {
    private val a = "a@gmail.com"
    private val b = "b@gmail.com"
    private val notifier = RecordingNotifier()
    private val settings = FixedCheckSettings()
    private lateinit var database: UltimateCalendarDatabase
    private lateinit var notified: NotifiedInvitations
    private lateinit var source: FakeCalendarSource

    private fun calendar(id: Long, address: String) = CalendarInfo(
        CalendarId(id),
        CalendarAccount(address, "com.google"),
        "c$id",
        0,
        CalendarAccess.OWNER,
        ownerEmail = address
    )

    @BeforeEach
    fun open() {
        database = inMemoryDatabase()
        notified = NotifiedInvitations(database.notifiedInvitationDao(), Dispatchers.Unconfined)
        source = FakeCalendarSource(listOf(calendar(1, a), calendar(2, b)))
    }

    @AfterEach
    fun close() = database.close()

    private fun checker() = InvitationChecker(
        source,
        RecordingSyncRequester(),
        notified,
        notifier,
        settings,
        MutableClock(now),
        Dispatchers.Unconfined
    )

    private suspend fun create(calendar: Long, guests: List<Attendee>, uid: String = "uid-1") = (
        source.create(
            EventDraft(
                calendarId = CalendarId(calendar),
                title = "Dinner",
                time = EventTime.Timed(
                    now.plusSeconds(3600),
                    now.plusSeconds(7200),
                    ZoneOffset.UTC
                ),
                attendees = guests
            )
        ) as CalendarResult.Success
        ).value.also { source.setUid(it, uid) }

    private fun organizer() = Attendee.of(a, status = AttendeeStatus.ACCEPTED, isOrganizer = true)

    private fun done(outcome: InvitationCheckOutcome) =
        assertInstanceOf(InvitationCheckOutcome.Done::class.java, outcome)

    private fun key(calendar: Long, event: EventId, address: String = "") =
        InvitationKey(CalendarId(calendar), event, address)

    @Test
    fun `an event of A that invites my account B notifies once, for B, and then stays quiet`() =
        runTest {
            val event = create(1, listOf(organizer(), Attendee.of(b)))

            val first = done(checker().check(false))
            val second = done(checker().check(false))

            val invitation = notifier.calls.single().new.single()
            assertEquals(key(1, event, b), invitation.key)
            assertEquals(b, invitation.account)
            assertEquals(1, first.pending)
            assertTrue(second.changes.isEmpty)
            assertEquals(listOf(invitation), notified.load())
        }

    @Test
    fun `it is found even when the event does not list the owner of its own calendar`() = runTest {
        val event = create(1, listOf(Attendee.of(b)))

        done(checker().check(false))

        assertEquals(key(1, event, b), notifier.calls.single().new.single().key)
    }

    @Test
    fun `the tray lists it without notifying or recording anything`() = runTest {
        val event = create(1, listOf(organizer(), Attendee.of(b)))

        val pending = (checker().pending() as CalendarResult.Success).value

        assertEquals(listOf(key(1, event, b)), pending.map { it.key })
        assertTrue(notifier.calls.isEmpty())
        assertEquals(emptyList<Any>(), notified.load())
    }

    @Test
    fun `when B receives its copy the notification moves to it and answering there settles it`() =
        runTest {
            val organised = create(1, listOf(organizer(), Attendee.of(b)))
            checker().check(false)

            val copy = create(2, listOf(organizer(), Attendee.of(b)))
            val arrived = done(checker().check(false))

            assertEquals(listOf(key(2, copy)), arrived.changes.new.map { it.key })
            assertEquals(
                listOf(key(1, organised, b)),
                arrived.changes.answeredElsewhere.map { it.key }
            )
            assertEquals(listOf(key(2, copy)), notified.load().map { it.key })

            source.respond(copy, AttendeeStatus.ACCEPTED)
            val answered = done(checker().check(false))

            assertEquals(0, answered.pending)
            assertEquals(listOf(key(2, copy)), answered.changes.answeredElsewhere.map { it.key })
        }

    @Test
    fun `an event that invites both of my accounts is two invitations`() = runTest {
        val event = create(1, listOf(Attendee.of("c@x.org"), Attendee.of(a), Attendee.of(b)))

        val outcome = done(checker().check(false))

        assertEquals(2, outcome.pending)
        assertEquals(
            setOf(key(1, event), key(1, event, b)),
            notifier.calls.single().new.map { it.key }.toSet()
        )
    }
}
