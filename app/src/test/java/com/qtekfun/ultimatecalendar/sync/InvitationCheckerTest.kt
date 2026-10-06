// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.calendar
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.invitation
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.now
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InvitationCheckerTest {
    private val clock = MutableClock(now)
    private val notifier = RecordingNotifier()
    private val syncs = RecordingSyncRequester()
    private val settings = FixedCheckSettings()
    private lateinit var database: UltimateCalendarDatabase
    private lateinit var notified: NotifiedInvitations
    private lateinit var source: FakeCalendarSource

    @BeforeEach
    fun open() {
        database = inMemoryDatabase()
        notified = NotifiedInvitations(database.notifiedInvitationDao(), Dispatchers.Unconfined)
        source = FakeCalendarSource(listOf(calendar(1), calendar(2)))
    }

    @AfterEach
    fun close() = database.close()

    private fun checker(on: CalendarSource = source) =
        InvitationChecker(on, syncs, notified, notifier, settings, clock, Dispatchers.Unconfined)

    private suspend fun create(draft: EventDraft) =
        (source.create(draft) as CalendarResult.Success).value

    private fun done(outcome: InvitationCheckOutcome) =
        assertInstanceOf(InvitationCheckOutcome.Done::class.java, outcome)

    @Test
    fun `a pending invitation in any calendar is notified once as new`() = runTest {
        val first = create(invitation("Lunch", calendar = 1))
        create(invitation("Dinner", calendar = 2, start = now.plusSeconds(7200)))

        val outcome = done(checker().check(requestSync = false))

        assertEquals(2, outcome.pending)
        assertEquals(1, notifier.calls.size)
        assertEquals(
            listOf("Lunch", "Dinner"),
            notifier.calls.single().new.map { it.title }
        )
        assertEquals(first, notifier.calls.single().new.first().key.eventId)
        assertEquals(2, notified.load().size)
    }

    @Test
    fun `a second check with nothing new notifies nothing`() = runTest {
        create(invitation())
        checker().check(false)

        val outcome = done(checker().check(false))

        assertTrue(outcome.changes.isEmpty)
        assertEquals(1, notifier.calls.size)
    }

    @Test
    fun `events the user already answered or is not invited to are not notified`() = runTest {
        val answered = create(invitation("Answered"))
        source.respond(answered, AttendeeStatus.ACCEPTED)
        create(invitation("Not mine", attendee = "other@example.com"))

        val outcome = done(checker().check(false))

        assertEquals(0, outcome.pending)
        assertTrue(notifier.calls.isEmpty())
    }

    @Test
    fun `moving the event notifies the change once and then stays quiet`() = runTest {
        val id = create(invitation("Lunch"))
        checker().check(false)
        val event = (source.event(id) as CalendarResult.Success).value
        val later = now.plusSeconds(10_800)
        source.update(
            event.copy(time = EventTime.Timed(later, later.plusSeconds(3600), ZoneOffset.UTC))
        )

        val moved = done(checker().check(false))
        val again = done(checker().check(false))

        assertEquals(listOf(id), moved.changes.changed.map { it.current.key.eventId })
        assertEquals(later, (notified.load().single().time as EventTime.Timed).start)
        assertTrue(again.changes.isEmpty)
        assertEquals(2, notifier.calls.size)
    }

    @Test
    fun `deleting the event is reported as cancelled and forgotten`() = runTest {
        val id = create(invitation())
        checker().check(false)
        source.delete(id)

        val outcome = done(checker().check(false))

        assertEquals(listOf(id), outcome.changes.cancelled.map { it.key.eventId })
        assertTrue(notified.load().isEmpty())
    }

    @Test
    fun `answering elsewhere is reported once and forgotten`() = runTest {
        val id = create(invitation())
        checker().check(false)
        source.respond(id, AttendeeStatus.DECLINED)

        val outcome = done(checker().check(false))

        assertEquals(listOf(id), outcome.changes.answeredElsewhere.map { it.key.eventId })
        assertTrue(notified.load().isEmpty())
    }

    @Test
    fun `an invitation whose event has just passed is dropped without a cancellation`() = runTest {
        create(invitation("Lunch", start = now.plusSeconds(3600)))
        checker().check(false)
        // Three hours on, the event is over: the source no longer returns it for "from now".
        clock.now = now.plusSeconds(3 * 3600)

        val outcome = done(checker().check(false))

        assertTrue(outcome.changes.isEmpty)
        assertTrue(notified.load().isEmpty())
    }

    @Test
    fun `an alias makes an invitation addressed to it pending, without it nothing is`() = runTest {
        create(invitation("To my alias", attendee = "alias@example.com"))

        assertEquals(0, done(checker().check(false)).pending)

        settings.aliases = setOf("alias@example.com")
        val outcome = done(checker().check(false))

        assertEquals(1, outcome.pending)
        assertEquals("To my alias", notifier.calls.single().new.single().title)
    }

    @Test
    fun `with aliases only the events that list attendees are read one by one`() = runTest {
        create(invitation("To my alias", attendee = "alias@example.com"))
        create(invitation("Not mine", attendee = "someone@example.com"))
        create(
            EventDraft(
                CalendarId(1),
                "Plain",
                EventTime.Timed(now.plusSeconds(60), now.plusSeconds(120), ZoneOffset.UTC)
            )
        )
        settings.aliases = setOf("alias@example.com")
        val counting = CountingReads(source)

        val outcome = done(checker(counting).check(false))

        assertEquals(1, outcome.pending)
        // The two events with attendees are read; the plain one cannot be an invitation.
        assertEquals(2, counting.reads)
    }

    @Test
    fun `a sync is requested for the accounts only when asked`() = runTest {
        checker().check(requestSync = false)
        assertTrue(syncs.requests.isEmpty())

        checker().check(requestSync = true)
        assertEquals(listOf(setOf(CheckFixtures.account)), syncs.requests)
    }

    @Test
    fun `only a manual check asks for an urgent sync, the others are background`() = runTest {
        checker().check(requestSync = true)
        checker().check(requestSync = true, manual = true)

        assertEquals(listOf(SyncReason.BACKGROUND, SyncReason.MANUAL), syncs.reasons)
    }

    @Test
    fun `a check killed halfway records nothing and the next one notifies it all`() = runTest {
        create(invitation("Lunch"))
        create(invitation("Dinner", start = now.plusSeconds(7200)))
        val stalled = CompletableDeferred<Unit>()
        val reading = launch(Dispatchers.Unconfined) {
            checker(StallingSource(source, stalled)).check(false)
        }
        // Cancel only once the source is stalled reading the first event, as a kill would: before
        // that the check may still be inside a Room query, and cancelling it there interrupts SQLite.
        stalled.await()
        reading.cancel(CancellationException("process killed"))
        reading.join()

        assertTrue(notifier.calls.isEmpty())
        assertTrue(notified.load().isEmpty())

        val next = done(checker().check(false))

        assertEquals(2, next.changes.new.size)
        assertEquals(2, notified.load().size)
    }

    @Test
    fun `a notifier that fails leaves the record alone so the next check notifies again`() =
        runTest {
            create(invitation())
            notifier.failure = IllegalStateException("cannot post")

            assertThrows<IllegalStateException> { checker().check(false) }
            assertTrue(notified.load().isEmpty())

            notifier.failure = null
            val outcome = done(checker().check(false))

            assertEquals(1, outcome.changes.new.size)
            assertEquals(1, notified.load().size)
        }

    @Test
    fun `a source that fails reading calendars, instances or events records nothing`() = runTest {
        create(invitation())
        val failure = CalendarResult.Failure(CalendarError.SourceFailure("down"))

        val calendars = checker(Broken(source, calendars = failure)).check(false)
        val instances = checker(Broken(source, instances = failure)).check(false)
        val event = checker(Broken(source, event = failure)).check(false)

        listOf(calendars, instances, event).forEach {
            assertEquals(InvitationCheckOutcome.Failed(failure.error), it)
        }
        assertTrue(notifier.calls.isEmpty())
        assertTrue(notified.load().isEmpty())
    }

    @Test
    fun `an event deleted while reading is skipped, not a failure`() = runTest {
        create(invitation())
        val gone = CalendarResult.Failure(CalendarError.NotFound)

        val outcome = done(checker(Broken(source, event = gone)).check(false))

        assertEquals(0, outcome.pending)
    }

    @Test
    fun `a revoked permission mid-check is a failure, not a crash`() = runTest {
        val outcome = checker(Throwing(source)).check(false)

        assertEquals(InvitationCheckOutcome.Failed(CalendarError.PermissionDenied), outcome)
        assertTrue(notifier.calls.isEmpty())
    }

    private class StallingSource(
        private val inner: CalendarSource,
        private val stalled: CompletableDeferred<Unit>
    ) : CalendarSource by inner {
        override suspend fun event(id: EventId): CalendarResult<Event> {
            stalled.complete(Unit)
            awaitCancellation()
        }
    }

    private class CountingReads(private val inner: CalendarSource) : CalendarSource by inner {
        var reads = 0
            private set

        override suspend fun event(id: EventId): CalendarResult<Event> {
            reads++
            return inner.event(id)
        }
    }

    private class Throwing(private val inner: CalendarSource) : CalendarSource by inner {
        override suspend fun calendars(): CalendarResult<List<CalendarInfo>> =
            throw SecurityException("READ_CALENDAR revoked")
    }

    /** The real fake, except for the calls given a canned result. */
    private class Broken(
        private val inner: CalendarSource,
        private val calendars: CalendarResult<List<CalendarInfo>>? = null,
        private val instances: CalendarResult<List<EventInstance>>? = null,
        private val event: CalendarResult<Event>? = null
    ) : CalendarSource by inner {
        override suspend fun calendars() = calendars ?: inner.calendars()

        override suspend fun instances(range: TimeRange, calendarIds: Set<CalendarId>?) =
            instances ?: inner.instances(range, calendarIds)

        override suspend fun event(id: EventId) = event ?: inner.event(id)
    }
}
