// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.reliability

import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationChanges
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationNotifier
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.calendar
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.invitation
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.now
import com.qtekfun.ultimatecalendar.sync.FixedCheckSettings
import com.qtekfun.ultimatecalendar.sync.InvitationCheckOutcome
import com.qtekfun.ultimatecalendar.sync.InvitationChecker
import com.qtekfun.ultimatecalendar.sync.MutableClock
import com.qtekfun.ultimatecalendar.sync.RecordingNotifier
import com.qtekfun.ultimatecalendar.sync.RecordingSyncRequester
import java.time.Duration
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
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

/** A notifier that is still posting when the process dies. */
private class HangingNotifier : InvitationNotifier {
    val posted = mutableListOf<InvitationChanges>()
    val posting = CompletableDeferred<Unit>()

    override suspend fun notify(changes: InvitationChanges) {
        posted += changes
        posting.complete(Unit)
        awaitCancellation()
    }
}

/** A source whose process dies at its [killAt]-th read, whichever read that is. */
private class DyingSource(
    private val delegate: CalendarSource,
    private val killAt: Int = Int.MAX_VALUE
) : CalendarSource by delegate {
    var reads = 0
        private set

    private fun read() {
        if (++reads == killAt) throw CancellationException("the process was killed")
    }

    override suspend fun calendars() = delegate.calendars().also { read() }

    override suspend fun instances(range: TimeRange, calendarIds: Set<CalendarId>?) =
        delegate.instances(range, calendarIds).also { read() }

    override suspend fun event(id: EventId) = delegate.event(id).also { read() }
}

/**
 * RF-06, RF-07 and RF-08 over the real checker, detector, Room and the fake source: whatever the
 * system does to the process, the next run notifies what is still pending, once. A "new process"
 * is a new checker and notifier over the same database and source.
 */
class InvitationReliabilityTest {
    private val clock = MutableClock(now)
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

    private fun process(on: CalendarSource, notifier: InvitationNotifier) = InvitationChecker(
        on,
        RecordingSyncRequester(),
        notified,
        notifier,
        FixedCheckSettings(),
        clock,
        Dispatchers.Unconfined
    )

    private suspend fun create(draft: EventDraft) =
        (source.create(draft) as CalendarResult.Success).value

    private suspend fun event(id: EventId) = (source.event(id) as CalendarResult.Success).value

    private fun done(outcome: InvitationCheckOutcome) =
        assertInstanceOf(InvitationCheckOutcome.Done::class.java, outcome)

    private fun Event.movedTo(hours: Long): Event {
        val start = now.plus(Duration.ofHours(hours))
        return copy(time = EventTime.Timed(start, start.plusSeconds(3_600), ZoneOffset.UTC))
    }

    @Test
    fun `a process killed at any read leaves the record alone and the next run notifies it all`() =
        runTest {
            create(invitation("Lunch", calendar = 1))
            create(invitation("Dinner", calendar = 2, start = now.plusSeconds(7_200)))
            create(invitation("Breakfast", calendar = 1, start = now.plusSeconds(10_800)))
            val probe = DyingSource(source)
            process(probe, RecordingNotifier()).check(requestSync = false)
            val reads = probe.reads
            notified.replaceAll(emptyList())
            assertTrue(reads >= 5, "the run reads calendars, instances and each event: $reads")

            for (killAt in 1..reads) {
                val dying = RecordingNotifier()
                assertThrows<CancellationException> {
                    process(DyingSource(source, killAt), dying).check(requestSync = false)
                }
                assertEquals(emptyList<Any>(), notified.load(), "killed at read $killAt")
                assertTrue(dying.calls.isEmpty(), "killed at read $killAt")
            }

            val next = RecordingNotifier()
            val outcome = done(process(source, next).check(requestSync = false))
            assertEquals(3, outcome.pending)
            assertEquals(
                listOf("Lunch", "Dinner", "Breakfast"),
                next.calls.single().new.map { it.title }
            )
            assertEquals(3, notified.load().size)
            // And then it is quiet.
            assertTrue(done(process(source, next).check(false)).changes.isEmpty)
            assertEquals(1, next.calls.size)
        }

    @Test
    fun `a process killed while notifying is told the same changes by the next run`() = runTest {
        create(invitation("Lunch"))
        create(invitation("Dinner", calendar = 2))
        val dying = HangingNotifier()
        val posted = dying.posted
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            process(source, dying).check(false)
        }
        dying.posting.await()
        job.cancel()
        job.join()
        assertEquals(1, posted.size)
        assertEquals(emptyList<Any>(), notified.load())

        val next = RecordingNotifier()
        process(source, next).check(false)

        assertEquals(posted, next.calls)
        assertEquals(2, notified.load().size)
    }

    @Test
    fun `a notifier that cannot post leaves everything to the next run`() = runTest {
        create(invitation("Lunch"))
        val failing = RecordingNotifier().apply { failure = IllegalStateException("no channel") }
        assertThrows<IllegalStateException> { process(source, failing).check(false) }
        assertEquals(emptyList<Any>(), notified.load())

        val next = RecordingNotifier()
        process(source, next).check(false)

        assertEquals(listOf("Lunch"), next.calls.single().new.map { it.title })
    }

    @Test
    fun `hours without a periodic run notify what is pending and skip what started`() = runTest {
        create(invitation("Started", start = now.plusSeconds(3_600)))
        create(invitation("Soon", start = now.plusSeconds(3 * 3_600L)))
        create(invitation("Tomorrow", calendar = 2, start = now.plusSeconds(20 * 3_600L)))
        // Nothing ran: the job was frozen for two hours.
        clock.now = now.plusSeconds(2 * 3_600L)
        val next = RecordingNotifier()

        val outcome = done(process(source, next).check(false))

        assertEquals(2, outcome.pending)
        assertEquals(listOf("Soon", "Tomorrow"), next.calls.single().new.map { it.title })
        assertTrue(next.calls.single().cancelled.isEmpty())
    }

    @Test
    fun `edited, cancelled and answered invitations are each reported once, across restarts`() =
        runTest {
            val moved = create(invitation("Moved", start = now.plusSeconds(3_600)))
            val cancelled = create(invitation("Cancelled", start = now.plusSeconds(7_200)))
            val answered =
                create(invitation("Answered", calendar = 2, start = now.plusSeconds(9_000)))
            val first = RecordingNotifier()
            process(source, first).check(false)
            assertEquals(3, first.calls.single().new.size)

            // While the app was not running the organizer changed things.
            source.update(event(moved).movedTo(5))
            source.delete(cancelled)
            source.respond(answered, AttendeeStatus.ACCEPTED)
            val brandNew = create(invitation("Brand new", start = now.plusSeconds(14_400)))

            val second = RecordingNotifier()
            process(source, second).check(false)
            val changes = second.calls.single()
            assertEquals(listOf(brandNew), changes.new.map { it.key.eventId })
            assertEquals(listOf(moved), changes.changed.map { it.current.key.eventId })
            assertEquals(listOf(cancelled), changes.cancelled.map { it.key.eventId })
            assertEquals(listOf(answered), changes.answeredElsewhere.map { it.key.eventId })

            val third = RecordingNotifier()
            process(source, third).check(false)
            assertTrue(third.calls.isEmpty())
            assertEquals(listOf("Brand new", "Moved"), notified.load().map { it.title }.sorted())
        }

    @Test
    fun `a move whose notification was killed is notified again by the next run`() = runTest {
        val id = create(invitation("Lunch", start = now.plusSeconds(3_600)))
        process(source, RecordingNotifier()).check(false)
        source.update(event(id).movedTo(4))

        // The check that would report the move dies while posting.
        val dying = HangingNotifier()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            process(source, dying).check(false)
        }
        dying.posting.await()
        job.cancel()
        job.join()

        val next = RecordingNotifier()
        process(source, next).check(false)
        assertEquals(listOf(id), next.calls.single().changed.map { it.current.key.eventId })

        val after = RecordingNotifier()
        process(source, after).check(false)
        assertTrue(after.calls.isEmpty())
    }

    @Test
    fun `an invitation whose event is cancelled before it was ever notified is never notified`() =
        runTest {
            val id = create(invitation("Lunch"))
            source.delete(id)
            val next = RecordingNotifier()

            val outcome = done(process(source, next).check(false))

            assertEquals(0, outcome.pending)
            assertTrue(next.calls.isEmpty())
        }
}
