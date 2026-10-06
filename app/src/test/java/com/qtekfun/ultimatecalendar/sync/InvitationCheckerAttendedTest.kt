// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.invitations.AttendedEvents
import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.data.source.OwnEditMarkingSource
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.ME
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.calendar
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.invitation
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.now
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The events the user goes to: changes and cancellations after accepting (RF-07). */
class InvitationCheckerAttendedTest {
    private val boss = "boss@example.com"
    private val clock = MutableClock(now)
    private val notifier = RecordingNotifier()
    private lateinit var database: UltimateCalendarDatabase
    private lateinit var attended: AttendedEvents
    private lateinit var source: FakeCalendarSource

    @BeforeEach
    fun open() {
        database = inMemoryDatabase()
        attended = AttendedEvents(database.attendedEventDao(), Dispatchers.Unconfined)
        source = FakeCalendarSource(listOf(calendar(1)))
    }

    @AfterEach
    fun close() = database.close()

    private fun checker(on: CalendarSource = source) = InvitationChecker(
        on,
        RecordingSyncRequester(),
        NotifiedInvitations(database.notifiedInvitationDao(), Dispatchers.Unconfined),
        notifier,
        FixedCheckSettings(),
        clock,
        Dispatchers.Unconfined,
        attended = attended
    )

    private suspend fun read(id: EventId) = (source.event(id) as CalendarResult.Success).value

    /** An event organised by someone else that the user accepted. */
    private suspend fun accepted(
        title: String = "Standup",
        start: Instant = now.plusSeconds(3600),
        status: AttendeeStatus = AttendeeStatus.ACCEPTED
    ): EventId {
        val id = (source.create(invitation(title, start)) as CalendarResult.Success).value
        source.update(
            read(id).copy(
                organizer = boss,
                attendees = listOf(
                    Attendee.of(boss, isOrganizer = true, status = AttendeeStatus.ACCEPTED),
                    Attendee.of(ME, status = status)
                )
            )
        )
        return id
    }

    private suspend fun move(
        id: EventId,
        to: Instant,
        edit: suspend (Event) -> Unit = {
            source.update(it)
        }
    ) {
        edit(read(id).copy(time = EventTime.Timed(to, to.plusSeconds(3600), ZoneOffset.UTC)))
    }

    private fun done(outcome: InvitationCheckOutcome) =
        assertInstanceOf(InvitationCheckOutcome.Done::class.java, outcome)

    @Test
    fun `the first run follows what is accepted without telling anything`() = runTest {
        accepted("Standup")
        accepted("Maybe", status = AttendeeStatus.TENTATIVE)

        val outcome = done(checker().check(false))

        assertTrue(outcome.changes.isEmpty)
        assertTrue(notifier.calls.isEmpty())
        assertEquals(2, attended.load().size)
    }

    @Test
    fun `a moved event is told once, with the new time, and then stays quiet`() = runTest {
        val id = accepted()
        checker().check(false)
        val later = now.plusSeconds(10_800)
        move(id, later)

        val moved = done(checker().check(false))
        val again = done(checker().check(false))

        val change = moved.changes.attendedChanged.single()
        assertEquals(id, change.current.key.eventId)
        assertEquals(later, (change.current.time as EventTime.Timed).start)
        assertTrue(moved.changes.new.isEmpty() && moved.changes.changed.isEmpty())
        assertTrue(again.changes.isEmpty)
        assertEquals(1, notifier.calls.size)
        assertEquals(later, (attended.load().single().time as EventTime.Timed).start)
    }

    @Test
    fun `a new place is a change and the same place is not`() = runTest {
        val id = accepted()
        checker().check(false)
        source.update(read(id).copy(location = "Room 4"))

        val changed = done(checker().check(false))
        source.update(read(id).copy(location = " Room 4 "))
        val same = done(checker().check(false))

        assertEquals("Room 4", changed.changes.attendedChanged.single().current.location)
        assertTrue(same.changes.isEmpty)
    }

    @Test
    fun `an event moved beyond the window or into the past is found without reading the year`() =
        runTest {
            val far = accepted("Far")
            val past = accepted("Past")
            checker().check(false)
            move(far, now.plusSeconds(400L * 86_400))
            move(past, now.minusSeconds(86_400))

            val outcome = done(checker().check(false))

            assertEquals(
                listOf(far),
                outcome.changes.attendedChanged.map {
                    it.current.key.eventId
                }
            )
            assertEquals(listOf(past), outcome.changes.attendedDropped.map { it.eventId })
            assertTrue(outcome.changes.attendedCancelled.isEmpty())
            assertEquals(listOf(far), attended.load().map { it.key.eventId })
        }

    @Test
    fun `a deleted event is cancelled once and forgotten`() = runTest {
        val id = accepted("Planning")
        checker().check(false)
        source.delete(id)

        val first = done(checker().check(false))
        val second = done(checker().check(false))

        val gone = first.changes.attendedCancelled.single()
        assertEquals(id, gone.key.eventId)
        assertEquals("Planning", gone.title)
        assertTrue(second.changes.isEmpty)
        assertTrue(attended.load().isEmpty())
    }

    @Test
    fun `declining a followed event stops following it and tells nothing`() = runTest {
        val id = accepted()
        checker().check(false)
        source.respond(id, AttendeeStatus.DECLINED)

        val outcome = done(checker().check(false))

        assertEquals(listOf(id), outcome.changes.attendedDropped.map { it.eventId })
        assertTrue(outcome.changes.attendedChanged.isEmpty())
        assertTrue(outcome.changes.attendedCancelled.isEmpty())
        assertTrue(attended.load().isEmpty())
    }

    @Test
    fun `an event the user edits in this app is not told, but a later outside change is`() =
        runTest {
            val id = accepted()
            checker().check(false)
            val own = OwnEditMarkingSource(source, attended)
            val later = now.plusSeconds(10_800)
            move(id, later) { own.update(it) }

            val mine = done(checker().check(false))
            move(id, later.plusSeconds(3600))
            val theirs = done(checker().check(false))

            assertTrue(mine.changes.isEmpty)
            assertEquals(1, theirs.changes.attendedChanged.size)
            assertEquals(
                later.plusSeconds(3600),
                (attended.load().single().time as EventTime.Timed).start
            )
        }

    @Test
    fun `deleting an event in this app does not tell it was cancelled`() = runTest {
        val id = accepted()
        checker().check(false)
        OwnEditMarkingSource(source, attended).delete(id)

        val outcome = done(checker().check(false))

        assertTrue(outcome.changes.attendedCancelled.isEmpty())
        assertTrue(attended.load().isEmpty())
    }

    @Test
    fun `a pending invitation keeps its own path and is not told twice`() = runTest {
        val pending = (source.create(invitation("Pending")) as CalendarResult.Success).value
        source.update(
            read(pending).copy(
                organizer = boss,
                attendees = listOf(Attendee.of(boss, isOrganizer = true), Attendee.of(ME))
            )
        )
        val going = accepted("Going")
        checker().check(false)
        val later = now.plusSeconds(10_800)
        move(pending, later)
        move(going, later)

        val outcome = done(checker().check(false))

        assertEquals(listOf(pending), outcome.changes.changed.map { it.current.key.eventId })
        assertEquals(listOf(going), outcome.changes.attendedChanged.map { it.current.key.eventId })
    }

    @Test
    fun `accepting a pending invitation follows it from then on without a change`() = runTest {
        val id = (source.create(invitation("Pending")) as CalendarResult.Success).value
        source.update(
            read(id).copy(
                organizer = boss,
                attendees = listOf(Attendee.of(boss, isOrganizer = true), Attendee.of(ME))
            )
        )
        checker().check(false)
        source.respond(id, AttendeeStatus.ACCEPTED)

        val outcome = done(checker().check(false))

        assertEquals(listOf(id), outcome.changes.answeredElsewhere.map { it.key.eventId })
        assertTrue(outcome.changes.attendedChanged.isEmpty())
        assertEquals(listOf(id), attended.load().map { it.key.eventId })
    }

    @Test
    fun `a check that dies after notifying records nothing, so the change is told again`() =
        runTest {
            val id = accepted()
            checker().check(false)
            move(id, now.plusSeconds(10_800))
            notifier.failure = IllegalStateException("killed")
            assertThrows<IllegalStateException> { checker().check(false) }
            notifier.failure = null

            val retry = done(checker().check(false))

            assertEquals(1, retry.changes.attendedChanged.size)
        }

    @Test
    fun `an event that passes is pruned and the series advancing is quiet`() = runTest {
        accepted()
        checker().check(false)
        clock.now = now.plusSeconds(86_400)

        val outcome = done(checker().check(false))

        assertTrue(outcome.changes.attendedChanged.isEmpty())
        assertTrue(outcome.changes.attendedCancelled.isEmpty())
        assertTrue(attended.load().isEmpty())
    }
}
