// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import com.qtekfun.ultimatecalendar.data.local.StoredKey
import com.qtekfun.ultimatecalendar.data.local.entity.PendingOperationEntity
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceOverride
import com.qtekfun.ultimatecalendar.sync.conflict.EventField
import com.qtekfun.ultimatecalendar.sync.conflict.event
import com.qtekfun.ultimatecalendar.sync.queue.ProcessResult
import com.qtekfun.ultimatecalendar.sync.queue.QueuedOperation
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Sends queued changes to [FakeCalDav]: ETags, lost answers, conflicts and refusals. */
class PushSyncTest {
    @StartStop
    val server = MockWebServer()

    private lateinit var env: EngineFixtures

    @BeforeEach
    fun setUp() = runTest { env = EngineFixtures(server).setUp() }

    @AfterEach
    fun close() = env.db.close()

    private val href get() = env.work + "standup.ics"
    private val ops get() = env.db.pendingOperationDao()
    private val monday = OccurrenceKey.Moment(Instant.parse("2026-10-05T07:00:00Z"))
    private val tuesday = OccurrenceKey.Moment(Instant.parse("2026-10-06T07:00:00Z"))

    private fun puts() = env.fake.requests.count { it == "PUT $href" }

    private suspend fun waiting(): PendingOperationEntity = ops.all(env.account.id).single()

    private val foreign =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Other//EN\r\nBEGIN:VEVENT\r\n" +
            "UID:uid-x\r\nDTSTAMP:20261001T100000Z\r\nDTSTART:20261005T070000Z\r\n" +
            "DTEND:20261005T073000Z\r\nSUMMARY:Standup\r\nX-CUSTOM:keep me\r\nCATEGORIES:Work\r\n" +
            "END:VEVENT\r\nEND:VCALENDAR\r\n"

    @Test
    fun `a new event is created with If-None-Match and takes the ETag of the server`() = runTest {
        env.pullAll()
        env.createLocally(href, event(title = "Standup", location = "Room 1"))

        assertEquals(ProcessResult(done = 1), env.pushAll())

        val sent = env.fake.resources.getValue(href)
        assertTrue("SUMMARY:Standup" in sent.ics)
        assertTrue("LOCATION:Room 1" in sent.ics)
        val stored = env.row(href)
        assertEquals(sent.etag, stored.etag)
        assertEquals(sent.ics, stored.ics)
        assertEquals(0, stored.dirtyFields)
        assertEquals(emptyList<QueuedOperation>(), env.operations())
    }

    @Test
    fun `an update keeps what the app does not know and bumps the sequence`() = runTest {
        env.fake.put(href, foreign)
        env.pullAll()
        env.editLocally(href, EventField.TITLE) { it.master { copy(title = "Daily") } }

        assertEquals(ProcessResult(done = 1), env.pushAll())

        val sent = env.fake.resources.getValue(href).ics
        assertTrue("SUMMARY:Daily" in sent)
        assertTrue("X-CUSTOM:keep me" in sent)
        assertTrue("CATEGORIES:Work" in sent)
        assertTrue("PRODID:-//Other//EN" in sent)
        assertTrue("SEQUENCE:1" in sent)
        assertEquals(1, env.row(href).sequence)
        assertEquals(0, env.row(href).dirtyFields)
    }

    @Test
    fun `an update goes over the version it was based on, and a stale one is merged and retried`() =
        runTest {
            env.pulled(href, event(title = "Standup"))
            env.editLocally(href, EventField.TITLE) { it.master { copy(title = "Daily") } }
            env.fake.put(href, env.ics(event(title = "Standup", location = "Room 2")))

            assertEquals(ProcessResult(retried = 1), env.pushAll())

            // The server copy was merged in; the change is still to be sent.
            val merged = env.row(href)
            assertEquals("Daily", merged.title)
            assertEquals("Room 2", merged.location)
            assertEquals("\"2\"", merged.etag)
            assertEquals("Changed on the server", ops.all(env.account.id).first().lastError)

            // The retry sends the merge; the update the merge queued has then nothing left to send.
            env.clock.advance(Duration.ofSeconds(5))
            assertEquals(ProcessResult(done = 2), env.pushAll())

            val sent = env.fake.resources.getValue(href).ics
            assertTrue("SUMMARY:Daily" in sent)
            assertTrue("LOCATION:Room 2" in sent)
            assertEquals(2, puts())
        }

    @Test
    fun `a create whose answer was lost is found on the server and not made twice`() = runTest {
        env.pullAll()
        val created = env.createLocally(href, event(title = "Standup"))
        // The first run reached the server, the answer never came back.
        env.fake.put(href, env.ics(event(title = "Standup")))
        env.db.pendingOperationRetryDao().markStarted(waiting().id, env.clock.millis())

        assertEquals(ProcessResult(done = 1), env.pushAll())

        assertEquals(1, env.fake.resources.size)
        assertEquals("\"1\"", env.row(href).etag)
        assertEquals(created.id, env.row(href).id)
        assertEquals(1, puts())
        assertEquals(emptyList<QueuedOperation>(), env.operations())
    }

    @Test
    fun `pushing twice sends once`() = runTest {
        env.pullAll()
        env.createLocally(href)

        env.pushAll()
        assertEquals(ProcessResult(), env.pushAll())

        assertEquals(1, puts())
    }

    @Test
    fun `a push interrupted by the server is retried later with backoff and then goes through`() =
        runTest {
            env.pullAll()
            env.createLocally(href)
            env.fake.failures[href] = ArrayDeque(listOf(503))

            assertEquals(ProcessResult(retried = 1), env.pushAll())
            assertEquals("HTTP 503", waiting().lastError)
            assertEquals(env.clock.now.plusSeconds(5).toEpochMilli(), waiting().nextAttemptAt)
            assertEquals(ProcessResult(), env.pushAll())

            env.clock.advance(Duration.ofSeconds(5))
            assertEquals(ProcessResult(done = 1), env.pushAll())
            assertEquals(2, puts())
            assertEquals(1, env.fake.resources.size)
        }

    @Test
    fun `a gone resource or one the server cannot find means the event is gone`() = runTest {
        env.pulled(href)
        env.editLocally(href, EventField.TITLE) { it.master { copy(title = "Daily") } }
        env.fake.failures[href] = ArrayDeque(listOf(404))

        assertEquals(ProcessResult(done = 1), env.pushAll())

        // Changed here and gone there: the user chooses.
        assertTrue(env.row(href).deletedOnServer)

        val other = env.work + "other.ics"
        env.pulled(other, event(title = "Other"))
        env.fake.failures[other] = ArrayDeque(listOf(410))
        env.editLocally(other, EventField.TITLE) { it.master { copy(title = "Other 2") } }
        assertEquals(ProcessResult(done = 1), env.pushAll())
        assertTrue(env.row(other).deletedOnServer)
    }

    @Test
    fun `an update to something deleted on the server meanwhile is a 412 that finds nothing`() =
        runTest {
            env.pulled(href)
            env.editLocally(href, EventField.TITLE) { it.master { copy(title = "Daily") } }
            env.fake.remove(href)

            assertEquals(ProcessResult(done = 1), env.pushAll())

            assertTrue(env.row(href).deletedOnServer)
            assertEquals(emptyList<QueuedOperation>(), env.operations())
        }

    @Test
    fun `refusals fail the operation, server errors retry it`() = runTest {
        env.pulled(href)
        env.editLocally(href, EventField.TITLE) { it.master { copy(title = "Daily") } }

        env.fake.failures[href] = ArrayDeque(listOf(403))
        assertEquals(ProcessResult(failed = 1), env.pushAll())
        assertEquals("Read-only calendar", waiting().lastError)
        env.queue.retry(waiting().id)

        env.fake.failures[href] = ArrayDeque(listOf(400))
        assertEquals(ProcessResult(failed = 1), env.pushAll())
        assertEquals("HTTP 400", waiting().lastError)
        env.queue.retry(waiting().id)

        env.fake.failures[href] = ArrayDeque(listOf(401))
        assertEquals(ProcessResult(retried = 1), env.pushAll())
        assertEquals("Unauthorized", waiting().lastError)
    }

    @Test
    fun `a delete removes the event on the server and here, whatever version the server has`() =
        runTest {
            env.pulled(href)
            val row = env.row(href)
            env.events.update(row.copy(deleted = true))
            env.queue.enqueue(env.account.id, row.id, QueuedOperation.DeleteEvent(href))
            env.fake.put(href, env.ics(event(title = "Changed meanwhile")))

            assertEquals(ProcessResult(done = 1), env.pushAll())

            assertFalse(href in env.fake.resources)
            assertNull(env.events.byHref(env.account.id, href))
        }

    @Test
    fun `a delete of something already gone is done`() = runTest {
        listOf(404, 410).forEach { code ->
            val gone = env.work + "gone-$code.ics"
            val row = env.pulled(gone)
            env.events.update(row.copy(deleted = true))
            env.queue.enqueue(env.account.id, row.id, QueuedOperation.DeleteEvent(gone))
            env.fake.failures[gone] = ArrayDeque(listOf(code))

            assertEquals(ProcessResult(done = 1), env.pushAll())
            assertNull(env.events.byHref(env.account.id, gone))
        }
    }

    @Test
    fun `a delete refused or failing stays in the queue`() = runTest {
        val row = env.pulled(href)
        env.events.update(row.copy(deleted = true))
        env.queue.enqueue(env.account.id, row.id, QueuedOperation.DeleteEvent(href))

        env.fake.failures[href] = ArrayDeque(listOf(403))
        assertEquals(ProcessResult(failed = 1), env.pushAll())
        env.queue.retry(waiting().id)

        env.fake.failures[href] = ArrayDeque(listOf(500))
        assertEquals(ProcessResult(retried = 1), env.pushAll())
        assertEquals("HTTP 500", waiting().lastError)
        assertTrue(env.row(href).deleted)
    }

    @Test
    fun `an answer is applied again after a newer server change that would have lost it`() =
        runTest {
            val me = Attendee.of("ana@example.com", status = AttendeeStatus.NEEDS_ACTION)
            val invited = event(
                organizer = "bo@example.com",
                attendees = listOf(Attendee.of("bo@example.com", isOrganizer = true), me)
            )
            env.pulled(href, invited)
            env.editLocally(href, EventField.ATTENDEES, at = env.clock.instant().minusSeconds(60)) {
                it.master {
                    copy(
                        attendees = attendees.map { a ->
                            if (a.email == me.email) a.copy(status = AttendeeStatus.ACCEPTED) else a
                        }
                    )
                }
            }
            env.queue.discard(waiting().id)
            env.queue.enqueue(
                env.account.id,
                env.row(href).id,
                QueuedOperation.Respond("ana@example.com", AttendeeStatus.ACCEPTED, null)
            )
            // The organizer edits the invitation after the answer: the server's guest list is newer.
            env.clock.advance(Duration.ofMinutes(5))
            env.fake.put(
                href,
                env.ics(
                    invited.master {
                        copy(
                            location = "Room 9",
                            attendees =
                                attendees + Attendee.of("cy@example.com")
                        )
                    }
                )
            )

            assertEquals(ProcessResult(retried = 1), env.pushAll())
            // The merge took the server's guests: the answer is gone from the row...
            val guests = env.series(href).series.event.attendees.associateBy { it.email }
            assertEquals(AttendeeStatus.NEEDS_ACTION, guests.getValue("ana@example.com").status)
            env.clock.advance(Duration.ofSeconds(5))

            // ...and put back when the operation runs again.
            assertEquals(ProcessResult(done = 1), env.pushAll())
            val sent = env.fake.resources.getValue(href).ics
            assertTrue("PARTSTAT=ACCEPTED" in sent, sent)
            assertTrue("LOCATION:Room 9" in sent)
        }

    @Test
    fun `an answer for one occurrence only changes that one, and only if it is there`() = runTest {
        val me = Attendee.of("ana@example.com", status = AttendeeStatus.NEEDS_ACTION)
        val series =
            event(rrule = "FREQ=DAILY", organizer = "bo@example.com", attendees = listOf(me))
        val replacement = series.series.event.copy(title = "Moved", rrule = null)
        env.pulled(
            href,
            series.copy(
                series = series.series.copy(
                    overrides = listOf(
                        OccurrenceOverride(monday, replacement),
                        OccurrenceOverride(tuesday, null)
                    )
                )
            )
        )
        val row = env.row(href)
        val answer = QueuedOperation.Respond(
            "ANA@example.com",
            AttendeeStatus.DECLINED,
            StoredKey.of(monday)
        )
        env.queue.enqueue(env.account.id, row.id, answer)

        assertEquals(ProcessResult(done = 1), env.pushAll())

        val read = env.series(href).series
        assertEquals(AttendeeStatus.NEEDS_ACTION, read.event.attendees.single().status)
        val overrides = read.overrides.associateBy { it.recurrenceId }
        assertEquals(
            AttendeeStatus.DECLINED,
            overrides.getValue(monday).replacement?.attendees?.single()?.status
        )
        assertNull(overrides.getValue(tuesday).replacement)

        // The cancelled occurrence has nobody to answer: nothing changes, nothing is sent.
        val before = env.fake.requests.size
        env.queue.enqueue(
            env.account.id,
            row.id,
            QueuedOperation.Respond(
                "ana@example.com",
                AttendeeStatus.DECLINED,
                StoredKey.of(tuesday)
            )
        )
        assertEquals(ProcessResult(done = 1), env.pushAll())
        assertEquals(before, env.fake.requests.size)
    }

    @Test
    fun `an answer from somebody who is not a guest changes nothing and sends nothing`() = runTest {
        env.pulled(href, event(attendees = listOf(Attendee.of("bo@example.com"))))
        val row = env.row(href)
        env.queue.enqueue(
            env.account.id,
            row.id,
            QueuedOperation.Respond("nobody@example.com", AttendeeStatus.ACCEPTED, null)
        )
        val before = env.fake.requests.size

        assertEquals(ProcessResult(done = 1), env.pushAll())

        assertEquals(before, env.fake.requests.size)
    }

    @Test
    fun `a cancelled occurrence stays cancelled when the server edited it meanwhile`() = runTest {
        val series = event(rrule = "FREQ=DAILY")
        val edited = series.series.event.copy(title = "Moved", rrule = null)
        env.pulled(href, series)
        env.editLocally(href, EventField.OVERRIDES, at = env.clock.instant().minusSeconds(60)) {
            it.copy(series = it.series.copy(overrides = listOf(OccurrenceOverride(monday, null))))
        }
        env.queue.discard(waiting().id)
        env.queue.enqueue(
            env.account.id,
            env.row(href).id,
            QueuedOperation.CancelInstance(StoredKey.of(monday))
        )
        env.clock.advance(Duration.ofMinutes(5))
        env.fake.put(
            href,
            env.ics(
                series.copy(
                    series = series.series.copy(
                        overrides = listOf(OccurrenceOverride(monday, edited))
                    )
                )
            )
        )

        assertEquals(ProcessResult(retried = 1), env.pushAll())
        assertEquals("Moved", env.series(href).series.overrides.single().replacement?.title)
        env.clock.advance(Duration.ofSeconds(5))
        assertEquals(ProcessResult(done = 1), env.pushAll())

        val after = env.series(href).series.overrides
        assertEquals(listOf(monday), after.map { it.recurrenceId })
        assertNull(after.single().replacement)
        assertTrue("STATUS:CANCELLED" in env.fake.resources.getValue(href).ics)
    }

    @Test
    fun `cancelling an occurrence with no override adds one, and doing it again changes nothing`() =
        runTest {
            env.pulled(href, event(rrule = "FREQ=DAILY"))
            val row = env.row(href)
            val cancel = QueuedOperation.CancelInstance(StoredKey.of(tuesday))
            env.queue.enqueue(env.account.id, row.id, cancel)

            assertEquals(ProcessResult(done = 1), env.pushAll())
            assertEquals(listOf(tuesday), env.series(href).series.overrides.map { it.recurrenceId })
            assertEquals(1, puts())

            env.queue.enqueue(env.account.id, row.id, cancel)
            assertEquals(ProcessResult(done = 1), env.pushAll())
            assertEquals(1, puts())
        }

    @Test
    fun `nothing is sent for an event the server already has as it is`() = runTest {
        env.pulled(href)
        val row = env.row(href)
        env.queue.enqueue(env.account.id, row.id, QueuedOperation.UpdateEvent)
        val before = env.fake.requests.size

        assertEquals(ProcessResult(done = 1), env.pushAll())

        assertEquals(before, env.fake.requests.size)
    }

    @Test
    fun `texts waiting for the user keep the server text on the wire and stay marked`() = runTest {
        env.pulled(href, event(title = "Standup", description = "Old", location = "Room 1"))
        env.editLocally(
            href,
            EventField.TITLE,
            EventField.DESCRIPTION,
            EventField.LOCATION,
            EventField.AVAILABILITY,
            at = env.clock.instant().minusSeconds(60)
        ) {
            it.master {
                copy(
                    title = "Daily",
                    description = "Mine",
                    location = "Hall",
                    availability = Availability.FREE
                )
            }
        }
        env.queue.discard(waiting().id)
        env.clock.advance(Duration.ofMinutes(5))
        env.fake.put(
            href,
            env.ics(event(title = "Planning", description = "Theirs", location = "Room 2"))
        )
        env.pullAll()
        // Title, description and location are in conflict; the availability is still ours to send.
        assertEquals("Planning", env.row(href).conflictTitle)
        assertEquals("Theirs", env.row(href).conflictDescription)
        assertEquals("Room 2", env.row(href).conflictLocation)
        // What is still ours to send was queued by the merge.
        assertEquals(listOf<QueuedOperation>(QueuedOperation.UpdateEvent), env.operations())

        assertEquals(ProcessResult(done = 1), env.pushAll())

        val sent = env.fake.resources.getValue(href).ics
        assertTrue("SUMMARY:Planning" in sent)
        assertTrue("DESCRIPTION:Theirs" in sent)
        assertTrue("LOCATION:Room 2" in sent)
        assertTrue("TRANSP:TRANSPARENT" in sent)
        val stored = env.row(href)
        assertEquals("Daily", stored.title)
        assertEquals(
            EventField.toBits(setOf(EventField.TITLE, EventField.DESCRIPTION, EventField.LOCATION)),
            stored.dirtyFields
        )

        // Only conflicts are left: there is nothing to send until the user chooses.
        env.queue.enqueue(env.account.id, stored.id, QueuedOperation.UpdateEvent)
        val before = env.fake.requests.size
        assertEquals(ProcessResult(done = 1), env.pushAll())
        assertEquals(before, env.fake.requests.size)
    }

    @Test
    fun `operations for an event that is gone, deleted or waiting for the user send nothing`() =
        runTest {
            val row = env.pulled(href)
            val other = env.pulled(env.work + "other.ics")
            val before = env.fake.requests.size

            env.queue.enqueue(env.account.id, 9_999, QueuedOperation.UpdateEvent)
            env.events.update(row.copy(deleted = true))
            env.queue.enqueue(env.account.id, row.id, QueuedOperation.UpdateEvent)
            env.events.update(other.copy(deletedOnServer = true, dirtyFields = 1))
            env.queue.enqueue(env.account.id, other.id, QueuedOperation.UpdateEvent)

            assertEquals(ProcessResult(done = 3), env.pushAll())
            assertEquals(before, env.fake.requests.size)
        }

    @Test
    fun `changes made while an event uploads stay to be sent`() = runTest {
        env.pulled(href, event(title = "Standup"))
        env.editLocally(href, EventField.TITLE) { it.master { copy(title = "Daily") } }
        env.fake.duringPut = {
            runBlocking {
                val row = env.row(href)
                env.events.update(
                    row.copy(
                        location = "Typed meanwhile",
                        dirtyFields =
                            row.dirtyFields or EventField.LOCATION.bit
                    )
                )
            }
        }

        assertEquals(ProcessResult(done = 1), env.pushAll())

        val stored = env.row(href)
        assertEquals("Typed meanwhile", stored.location)
        assertEquals(EventField.TITLE.bit or EventField.LOCATION.bit, stored.dirtyFields)
        assertTrue("SUMMARY:Daily" in stored.ics.orEmpty())
        assertFalse("Typed meanwhile" in env.fake.resources.getValue(href).ics)
    }

    @Test
    fun `an event deleted while it uploads is left alone`() = runTest {
        env.pulled(href)
        env.editLocally(href, EventField.TITLE) { it.master { copy(title = "Daily") } }
        env.fake.duringPut = { runBlocking { env.events.delete(env.row(href).id) } }

        assertEquals(ProcessResult(done = 1), env.pushAll())

        assertNull(env.events.byHref(env.account.id, href))
    }

    @Test
    fun `a conflict whose calendar vanished meanwhile is done`() = runTest {
        env.pulled(href)
        env.editLocally(href, EventField.TITLE) { it.master { copy(title = "Daily") } }
        env.fake.put(href, env.ics(event(title = "Standup", location = "Room 2")))
        env.fake.duringPut = { runBlocking { env.calendars.delete(env.calendar().id) } }

        assertEquals(ProcessResult(done = 1), env.pushAll())
    }

    @Test
    fun `a conflict whose download fails is retried`() = runTest {
        env.pulled(href)
        env.editLocally(href, EventField.TITLE) { it.master { copy(title = "Daily") } }
        env.fake.put(href, env.ics(event(title = "Standup", location = "Room 2")))
        env.fake.failOn = { method, _ -> if (method == "REPORT") 500 else null }

        assertEquals(ProcessResult(retried = 1), env.pushAll())
        assertEquals("HTTP 500", waiting().lastError)
    }
}
