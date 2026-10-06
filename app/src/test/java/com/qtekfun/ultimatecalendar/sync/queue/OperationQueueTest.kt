// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.queue

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.local.StoredKey
import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity
import com.qtekfun.ultimatecalendar.data.local.entity.PendingOperationEntity
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.local.model.OperationType
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.sync.queue.QueuedOperation.CancelInstance
import com.qtekfun.ultimatecalendar.sync.queue.QueuedOperation.CreateEvent
import com.qtekfun.ultimatecalendar.sync.queue.QueuedOperation.DeleteEvent
import com.qtekfun.ultimatecalendar.sync.queue.QueuedOperation.EditInstance
import com.qtekfun.ultimatecalendar.sync.queue.QueuedOperation.Respond
import com.qtekfun.ultimatecalendar.sync.queue.QueuedOperation.UpdateEvent
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OperationQueueTest {
    private val db = inMemoryDatabase()
    private val dao = db.pendingOperationDao()
    private val retryDao = db.pendingOperationRetryDao()
    private val clock = MutableClock()
    private val queue = OperationQueue(db, clock, FixedRandom(0.5))
    private val executor = ScriptedExecutor()
    private val monday = StoredKey(day = "2026-10-05")
    private val tuesday = StoredKey(day = "2026-10-06")
    private val nineOClock = StoredKey(at = 1_790_000_000_000)

    @AfterEach
    fun close() = db.close()

    private suspend fun account() = db.davAccountDao().insert(
        DavAccountEntity(serverUrl = "https://cloud.example.com/", loginName = "ana")
    )

    private suspend fun operations(accountId: Long) = dao.all(accountId).map {
        it.eventId to QueuedOperation.decode(it.payload)
    }

    /** Marks every queued operation as handed to the server, as a run cut short would. */
    private suspend fun started(accountId: Long) =
        dao.all(accountId).forEach { retryDao.markStarted(it.id, clock.now.toEpochMilli()) }

    private val accept = Respond("ana@example.com", AttendeeStatus.ACCEPTED, null)

    @Nested
    inner class Enqueue {
        @Test
        fun `stores the operation with its type, event, time and slot`() = runTest {
            val id = account()
            assertTrue(queue.enqueue(id, 7, EditInstance(monday)))
            val stored = dao.all(id).single()
            assertEquals(OperationType.EDIT_INSTANCE, stored.type)
            assertEquals(7L, stored.eventId)
            assertEquals(clock.now.toEpochMilli(), stored.createdAt)
            assertEquals("instance:2026-10-05", stored.slot)
            queue.enqueue(id, 8, UpdateEvent)
            assertNull(dao.all(id).last().slot)
            assertEquals(listOf(7L to EditInstance(monday), 8L to UpdateEvent), operations(id))
        }

        @Test
        fun `a create or update not sent yet covers every later change`() = runTest {
            val id = account()
            queue.enqueue(id, 1, CreateEvent)
            queue.enqueue(id, 1, UpdateEvent)
            queue.enqueue(id, 1, accept)
            queue.enqueue(id, 1, EditInstance(monday))
            queue.enqueue(id, 1, CancelInstance(tuesday))
            queue.enqueue(id, 2, UpdateEvent)
            queue.enqueue(id, 2, UpdateEvent)
            queue.enqueue(id, 2, accept)
            queue.enqueue(id, 2, EditInstance(monday))
            queue.enqueue(id, 2, CancelInstance(tuesday))
            assertEquals(listOf(1L to CreateEvent, 2L to UpdateEvent), operations(id))
        }

        @Test
        fun `a change after one that may have been sent is queued again`() = runTest {
            val id = account()
            queue.enqueue(id, 1, UpdateEvent)
            started(id)
            queue.enqueue(id, 1, UpdateEvent)
            queue.enqueue(id, 2, CreateEvent)
            started(id)
            queue.enqueue(id, 2, UpdateEvent)
            assertEquals(
                listOf(1L to UpdateEvent, 1L to UpdateEvent, 2L to CreateEvent, 2L to UpdateEvent),
                operations(id)
            )
        }

        @Test
        fun `a new answer replaces the one not sent yet for the same event and occurrence`() =
            runTest {
                val id = account()
                queue.enqueue(id, 1, accept)
                queue.enqueue(id, 1, Respond("ana@example.com", AttendeeStatus.DECLINED, null))
                assertEquals(
                    listOf(1L to Respond("ana@example.com", AttendeeStatus.DECLINED, null)),
                    operations(id)
                )
                assertEquals("respond:ana@example.com:null", dao.all(id).single().slot)
            }

        @Test
        fun `answers to other occurrences or from other addresses are kept apart`() = runTest {
            val id = account()
            queue.enqueue(id, 1, accept)
            queue.enqueue(id, 1, accept.copy(occurrence = monday))
            queue.enqueue(id, 1, accept.copy(occurrence = nineOClock))
            queue.enqueue(id, 1, accept.copy(email = "ana@work.example"))
            queue.enqueue(
                id,
                1,
                accept.copy(occurrence = monday, status = AttendeeStatus.TENTATIVE)
            )
            assertEquals(
                listOf(
                    accept,
                    accept.copy(occurrence = nineOClock),
                    accept.copy(email = "ana@work.example"),
                    accept.copy(occurrence = monday, status = AttendeeStatus.TENTATIVE)
                ).map { 1L to it },
                operations(id)
            )
        }

        @Test
        fun `an answer that may have been sent is not replaced`() = runTest {
            val id = account()
            queue.enqueue(id, 1, accept)
            started(id)
            queue.enqueue(id, 1, accept.copy(status = AttendeeStatus.DECLINED))
            assertEquals(
                listOf(accept, accept.copy(status = AttendeeStatus.DECLINED)).map { 1L to it },
                operations(id)
            )
        }

        @Test
        fun `a change to an occurrence replaces the one before, whatever it was`() = runTest {
            val id = account()
            queue.enqueue(id, 1, EditInstance(monday))
            queue.enqueue(id, 1, CancelInstance(monday))
            queue.enqueue(id, 1, EditInstance(tuesday))
            queue.enqueue(id, 1, EditInstance(nineOClock))
            queue.enqueue(id, 1, CancelInstance(nineOClock))
            assertEquals(
                listOf(
                    1L to CancelInstance(monday),
                    1L to EditInstance(tuesday),
                    1L to CancelInstance(nineOClock)
                ),
                operations(id)
            )
            assertEquals(OperationType.CANCEL_INSTANCE, dao.all(id).first().type)
        }

        @Test
        fun `deleting drops what was never sent or failed, then queues the delete`() = runTest {
            val id = account()
            queue.enqueue(id, 1, UpdateEvent)
            started(id)
            queue.enqueue(id, 1, CancelInstance(monday))
            retryDao.markFailed(dao.all(id).last().id, "read-only")
            queue.enqueue(id, 1, EditInstance(tuesday))
            queue.enqueue(id, 2, UpdateEvent)
            assertTrue(queue.enqueue(id, 1, DeleteEvent("/a/1.ics")))
            assertEquals(
                listOf(1L to UpdateEvent, 2L to UpdateEvent, 1L to DeleteEvent("/a/1.ics")),
                operations(id)
            )
        }

        @Test
        fun `an event created and deleted here never reaches the server`() = runTest {
            val id = account()
            queue.enqueue(id, 1, CreateEvent)
            queue.enqueue(id, 1, UpdateEvent)
            queue.enqueue(id, 2, UpdateEvent)
            assertFalse(queue.enqueue(id, 1, DeleteEvent("/a/1.ics")))
            assertEquals(listOf(2L to UpdateEvent), operations(id))
        }

        @Test
        fun `a create that may have been sent still needs its delete`() = runTest {
            val id = account()
            queue.enqueue(id, 1, CreateEvent)
            started(id)
            assertTrue(queue.enqueue(id, 1, DeleteEvent("/a/1.ics")))
            assertEquals(
                listOf(1L to CreateEvent, 1L to DeleteEvent("/a/1.ics")),
                operations(id)
            )
        }
    }

    @Nested
    inner class Process {
        @Test
        fun `runs everything in order and empties the queue`() = runTest {
            val id = account()
            queue.enqueue(id, 1, CreateEvent)
            queue.enqueue(id, 2, UpdateEvent)
            queue.enqueue(id, 1, DeleteEvent("/a/1.ics").also { started(id) })
            assertEquals(ProcessResult(done = 3), queue.process(id, executor))
            assertEquals(
                listOf(1L to CreateEvent, 2L to UpdateEvent, 1L to DeleteEvent("/a/1.ics")),
                executor.calls
            )
            assertEquals(emptyList<PendingOperationEntity>(), dao.all(id))
        }

        @Test
        fun `a temporary error postpones the event with backoff and lets others go on`() = runTest {
            val id = account()
            queue.enqueue(id, 1, CreateEvent)
            queue.enqueue(id, 1, EditInstance(monday).also { started(id) })
            queue.enqueue(id, 2, UpdateEvent)
            executor.on(1, { ExecutionResult.Retry("timeout") })
            assertEquals(ProcessResult(done = 1, retried = 1), queue.process(id, executor))
            assertEquals(listOf(1L to CreateEvent, 2L to UpdateEvent), executor.calls)
            val waiting = dao.all(id).first()
            assertEquals(1, waiting.attempts)
            assertEquals("timeout", waiting.lastError)
            assertEquals(clock.now.plusSeconds(5).toEpochMilli(), waiting.nextAttemptAt)
            assertEquals(ProcessResult(), queue.process(id, executor))
            clock.advance(Duration.ofSeconds(5))
            assertEquals(ProcessResult(done = 2), queue.process(id, executor))
            assertEquals(listOf(true, false, true, false), executor.maybeSent)
        }

        @Test
        fun `each failed attempt waits twice as long as the one before`() = runTest {
            val id = account()
            queue.enqueue(id, 1, UpdateEvent)
            executor.on(
                1,
                { ExecutionResult.Retry("a") },
                { ExecutionResult.Retry("b") },
                { ExecutionResult.Retry("c") }
            )
            val waits = (1..3).map {
                queue.process(id, executor)
                val waiting = dao.all(id).single()
                clock.now = Instant.ofEpochMilli(waiting.nextAttemptAt)
                waiting.nextAttemptAt - waiting.createdAt
            }
            assertEquals(listOf(5L, 15L, 35L), waits.map { it / 1000 })
            assertEquals(3, dao.all(id).single().attempts)
            assertEquals(ProcessResult(done = 1), queue.process(id, executor))
        }

        @Test
        fun `a refusal fails the operation until the user retries or discards it`() = runTest {
            val id = account()
            queue.enqueue(id, 1, UpdateEvent)
            executor.on(1, { ExecutionResult.Failed("read-only calendar") })
            assertEquals(ProcessResult(failed = 1), queue.process(id, executor))
            queue.observeFailed(id).test {
                assertEquals("read-only calendar", awaitItem().single().lastError)
                assertEquals(ProcessResult(), queue.process(id, executor))
                queue.retry(dao.all(id).single().id)
                assertEquals(emptyList<PendingOperationEntity>(), awaitItem())
            }
            executor.on(1, { ExecutionResult.Failed("still read-only") })
            queue.process(id, executor)
            queue.observePendingCount(id).test {
                assertEquals(1, awaitItem())
                queue.discard(dao.all(id).single().id)
                assertEquals(0, awaitItem())
            }
        }

        @Test
        fun `unexpected errors are retried, unreadable payloads fail`() = runTest {
            val id = account()
            queue.enqueue(id, 1, UpdateEvent)
            executor.on(1, { error("boom") })
            dao.insert(
                PendingOperationEntity(
                    accountId = id,
                    type = OperationType.UPDATE,
                    eventId = 2,
                    payload = "{\"op\":\"unknown\"}",
                    createdAt = clock.now.toEpochMilli()
                )
            )
            assertEquals(ProcessResult(retried = 1, failed = 1), queue.process(id, executor))
            assertEquals(listOf("boom", "Unreadable operation"), dao.all(id).map { it.lastError })
            dao.insert(
                PendingOperationEntity(
                    accountId = id,
                    type = OperationType.UPDATE,
                    eventId = 3,
                    payload = "not json",
                    createdAt = clock.now.toEpochMilli()
                )
            )
            assertEquals(ProcessResult(failed = 1), queue.process(id, executor))
        }

        @Test
        fun `cancellation stops the run and keeps the operation`() = runTest {
            val id = account()
            queue.enqueue(id, 1, UpdateEvent)
            executor.on(1, { throw CancellationException("stopped") })
            assertThrows<CancellationException> { queue.process(id, executor) }
            assertEquals(1, dao.all(id).size)
        }

        @Test
        fun `a queue rebuilt after the process died picks up what was left, maybe sent`() =
            runTest {
                val id = account()
                queue.enqueue(id, 1, CreateEvent)
                queue.enqueue(id, 2, accept)
                executor.on(1, { throw CancellationException("killed") })
                assertThrows<CancellationException> { queue.process(id, executor) }

                val afterRestart = OperationQueue(db, clock, FixedRandom(0.5))
                val survivor = ScriptedExecutor()
                assertEquals(ProcessResult(done = 2), afterRestart.process(id, survivor))
                assertEquals(
                    listOf(1L to CreateEvent, 2L to accept),
                    survivor.calls
                )
                assertEquals(listOf(true, false), survivor.maybeSent)
                assertEquals(emptyList<PendingOperationEntity>(), dao.all(id))
            }

        @Test
        fun `what was sent is not sent again by the next run`() = runTest {
            val id = account()
            queue.enqueue(id, 1, UpdateEvent)
            assertEquals(ProcessResult(done = 1), queue.process(id, executor))
            assertEquals(ProcessResult(), queue.process(id, executor))
            assertEquals(1, executor.calls.size)
        }

        @Test
        fun `the operations of another account are left alone`() = runTest {
            val first = account()
            val second = db.davAccountDao().insert(
                DavAccountEntity(serverUrl = "https://other.example.com/", loginName = "bo")
            )
            queue.enqueue(first, 1, UpdateEvent)
            queue.enqueue(second, 1, UpdateEvent)
            assertEquals(ProcessResult(done = 1), queue.process(first, executor))
            assertEquals(1, dao.all(second).size)
        }
    }

    @Test
    fun `backoff doubles up to an hour, spread by up to 20 percent`() {
        assertEquals(Duration.ofSeconds(5), RetryBackoff(FixedRandom(0.5)).delay(0))
        assertEquals(Duration.ofSeconds(40), RetryBackoff(FixedRandom(0.5)).delay(3))
        assertEquals(Duration.ofHours(1), RetryBackoff(FixedRandom(0.5)).delay(99))
        assertEquals(Duration.ofSeconds(4), RetryBackoff(FixedRandom(0.0)).delay(0))
        assertEquals(Duration.ofSeconds(6), RetryBackoff(FixedRandom(1.0)).delay(0))
    }

    @Test
    fun `payloads missing a field are unreadable`() {
        listOf(
            """{"op":"delete_event"}""",
            """{"op":"respond","email":"ana@example.com"}""",
            """{"op":"edit_instance"}""",
            """{"op":"cancel_instance"}"""
        ).forEach {
            assertThrows<SerializationException> { QueuedOperation.decode(it) }
        }
    }

    @Test
    fun `operations are equal only when everything they carry is`() {
        val operations = listOf(
            CreateEvent,
            UpdateEvent,
            DeleteEvent("/a/1.ics"),
            DeleteEvent("/a/2.ics"),
            accept,
            accept.copy(email = "bo@example.com"),
            accept.copy(status = AttendeeStatus.DECLINED),
            accept.copy(occurrence = monday),
            accept.copy(occurrence = tuesday),
            EditInstance(monday),
            EditInstance(nineOClock),
            CancelInstance(monday),
            CancelInstance(nineOClock)
        )
        operations.forEachIndexed { i, one ->
            operations.forEachIndexed { j, other ->
                assertEquals(i == j, one == other, "$one vs $other")
                if (i == j) assertEquals(one.hashCode(), other.hashCode())
            }
            assertNotEquals(one, null)
            assertNotEquals(one, "text")
        }
        assertEquals(accept, accept.copy())
        assertEquals(
            accept.copy(occurrence = monday).hashCode(),
            accept.copy(occurrence = monday).hashCode()
        )
    }

    @Test
    fun `payloads survive encoding`() {
        listOf(
            CreateEvent,
            UpdateEvent,
            DeleteEvent("/a/1.ics"),
            accept,
            accept.copy(occurrence = nineOClock),
            EditInstance(monday),
            CancelInstance(nineOClock)
        ).forEach {
            assertEquals(it, QueuedOperation.decode(QueuedOperation.encode(it)))
        }
    }
}
