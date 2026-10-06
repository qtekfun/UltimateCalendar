// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.queue

import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.entity.PendingOperationEntity
import com.qtekfun.ultimatecalendar.data.local.model.OperationType
import java.time.Clock
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException

/**
 * Persistent queue of local changes waiting for the server (SPEC §5). Changes are never lost:
 * an operation only leaves the queue once the server applied it or the user discards it. All
 * its state lives in Room, so a queue built after the process died picks up where it was.
 */
class OperationQueue @Inject constructor(
    database: UltimateCalendarDatabase,
    private val clock: Clock,
    random: Random
) {
    private val dao = database.pendingOperationDao()
    private val retryDao = database.pendingOperationRetryDao()
    private val backoff = RetryBackoff(random)

    /**
     * Queues [operation] for [eventId], folding it into what is already waiting. Every
     * operation but a delete sends the whole event when it runs, so:
     * - a create or update not sent yet covers every later change, which needs no operation;
     * - an answer or a change to one occurrence replaces one of the same kind and subject
     *   not sent yet, and is queued after the rest otherwise;
     * - a delete drops what was never sent and, for an event the server never had, everything.
     *
     * Returns false when the server will never hear of the event: it was created and deleted
     * here before reaching it, so the caller can simply forget it.
     */
    suspend fun enqueue(accountId: Long, eventId: Long, operation: QueuedOperation): Boolean {
        val waiting = dao.forEvent(accountId, eventId)
        val unsentCreate = waiting.any { it.neverSent(OperationType.CREATE) }
        val covered = unsentCreate || waiting.any { it.neverSent(OperationType.UPDATE) }
        when (operation) {
            QueuedOperation.CreateEvent -> insert(accountId, eventId, operation)

            QueuedOperation.UpdateEvent -> if (!covered) insert(accountId, eventId, operation)

            is QueuedOperation.DeleteEvent -> {
                dao.delete(waiting.filter { it.neverSent() || it.failed }.map { it.id })
                if (!unsentCreate) insert(accountId, eventId, operation)
            }

            is QueuedOperation.Respond,
            is QueuedOperation.EditInstance,
            is QueuedOperation.CancelInstance -> if (!covered) {
                dao.delete(
                    waiting.filter { it.neverSent() && it.slot == operation.slot }.map { it.id }
                )
                insert(accountId, eventId, operation)
            }
        }
        return !(operation is QueuedOperation.DeleteEvent && unsentCreate)
    }

    private suspend fun insert(accountId: Long, eventId: Long, operation: QueuedOperation) {
        dao.insert(
            PendingOperationEntity(
                accountId = accountId,
                type = operation.type,
                eventId = eventId,
                payload = QueuedOperation.encode(operation),
                slot = operation.slot,
                createdAt = clock.millis()
            )
        )
    }

    /**
     * Runs the operations that are due, in the order they were queued. An event whose operation
     * is waiting or failed keeps its later operations waiting too; other events go on.
     */
    suspend fun process(accountId: Long, executor: OperationExecutor): ProcessResult {
        val blocked = mutableSetOf<Long>()
        var result = ProcessResult()
        while (true) {
            val now = clock.millis()
            val next = dao.all(accountId).firstOrNull { op ->
                val runnable = op.eventId !in blocked && !op.failed && op.nextAttemptAt <= now
                if (!runnable) blocked += op.eventId
                runnable
            } ?: break
            val outcome = run(next, executor)
            result = result.plus(outcome)
            when (outcome) {
                ExecutionResult.Done -> dao.delete(next.id)

                is ExecutionResult.Retry -> {
                    retryDao.recordFailure(
                        next.id,
                        now + backoff.delay(next.attempts).toMillis(),
                        outcome.reason
                    )
                    blocked += next.eventId
                }

                is ExecutionResult.Failed -> {
                    retryDao.markFailed(next.id, outcome.reason)
                    blocked += next.eventId
                }
            }
        }
        return result
    }

    /** Unexpected errors count as temporary, so an operation is never dropped by accident. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun run(
        operation: PendingOperationEntity,
        executor: OperationExecutor
    ): ExecutionResult = try {
        val maybeSent = operation.startedAt != null
        val decoded = QueuedOperation.decode(operation.payload)
        retryDao.markStarted(operation.id, clock.millis())
        executor.execute(operation.eventId, decoded, maybeSent)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SerializationException) {
        ExecutionResult.Failed(UNREADABLE)
    } catch (error: Exception) {
        ExecutionResult.Retry(error.message)
    }

    /** Makes a failed operation run again on the next sync. */
    suspend fun retry(operationId: Long) = retryDao.resetForRetry(operationId, clock.millis())

    /** Drops a failed operation; the local change stays, it is just not sent. */
    suspend fun discard(operationId: Long) = dao.delete(operationId)

    fun observePendingCount(accountId: Long): Flow<Int> = dao.observeCount(accountId)

    fun observeFailed(accountId: Long): Flow<List<PendingOperationEntity>> =
        retryDao.observeFailed(accountId)

    /**
     * Never handed to the server, not even partly: changing or dropping it is safe. Every run
     * marks the operation started first, so retried and failed ones count as sent.
     */
    private fun PendingOperationEntity.neverSent(ofType: OperationType? = null) =
        startedAt == null && (ofType == null || type == ofType)

    private companion object {
        const val UNREADABLE = "Unreadable operation"
    }
}

/** How many operations a [OperationQueue.process] run sent, postponed and failed. */
data class ProcessResult(val done: Int = 0, val retried: Int = 0, val failed: Int = 0) {
    fun plus(outcome: ExecutionResult) = when (outcome) {
        ExecutionResult.Done -> copy(done = done + 1)
        is ExecutionResult.Retry -> copy(retried = retried + 1)
        is ExecutionResult.Failed -> copy(failed = failed + 1)
    }
}
