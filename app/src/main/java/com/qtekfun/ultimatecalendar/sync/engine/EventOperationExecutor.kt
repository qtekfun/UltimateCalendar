// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import com.qtekfun.ultimatecalendar.data.ical.IcsEvents
import com.qtekfun.ultimatecalendar.data.ical.IcsWriter
import com.qtekfun.ultimatecalendar.data.local.StoredSeries
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.entity.DavEventEntity
import com.qtekfun.ultimatecalendar.data.remote.caldav.CalDav
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavResult
import com.qtekfun.ultimatecalendar.sync.conflict.EventField
import com.qtekfun.ultimatecalendar.sync.queue.ExecutionResult
import com.qtekfun.ultimatecalendar.sync.queue.OperationExecutor
import com.qtekfun.ultimatecalendar.sync.queue.QueuedOperation
import java.time.Clock

/** Sends queued event operations with CalDAV. */
class EventOperationExecutor(
    private val dav: CalDav,
    database: UltimateCalendarDatabase,
    private val merger: EventMerger,
    private val clock: Clock
) : OperationExecutor {
    private val events = database.davEventDao()
    private val calendars = database.davCalendarDao()

    override suspend fun execute(
        eventId: Long,
        operation: QueuedOperation,
        maybeSent: Boolean
    ): ExecutionResult {
        val event = events.get(eventId)
        return when {
            operation is QueuedOperation.DeleteEvent -> delete(eventId, operation)

            // Gone meanwhile, or waiting for the user: nothing to send.
            event == null || event.deleted || event.deletedOnServer -> ExecutionResult.Done

            else -> upload(
                withIntent(event, operation),
                created = operation == QueuedOperation.CreateEvent && maybeSent
            )
        }
    }

    /** [event] with the intent of [operation] applied again, which makes what it changed dirty. */
    private suspend fun withIntent(
        event: DavEventEntity,
        operation: QueuedOperation
    ): DavEventEntity {
        val before = StoredSeries.read(event)
        val after = OperationIntents.apply(before, operation)
        if (after == before) return event
        val changed = EventField.entries.filter { it.read(before) != it.read(after) }
        return StoredSeries.write(event, after).copy(
            dirtyFields = event.dirtyFields or EventField.toBits(changed.toSet()),
            modifiedAt = clock.millis()
        ).also { events.update(it) }
    }

    /**
     * PUTs the event over the version it was based on. Text fields waiting for the user keep the
     * server text. [created] marks a create whose answer was lost: a 412 means it arrived.
     */
    private suspend fun upload(event: DavEventEntity, created: Boolean): ExecutionResult {
        val conflicted = conflictedFields(event)
        // Nothing but text waiting for the user differs from the server: there is nothing to send.
        if (event.etag != null &&
            (event.dirtyFields and EventField.toBits(conflicted).inv()) == 0
        ) {
            return ExecutionResult.Done
        }
        val base = event.ics?.let { ServerEvent.read(it, event.calendarId, event.id, clock.zone) }
        val sent = conflicted.fold(StoredSeries.read(event)) { local, field ->
            base?.let { field.write(local, it.event) } ?: local
        }
        val ics = IcsWriter.write(
            IcsEvents.write(base?.component, sent, clock.instant(), clock.zone)
        )
        return when (val result = dav.write.put(event.href, ics, event.etag)) {
            is DavResult.Success -> saved(event, ics, result.value, conflicted)

            DavResult.PreconditionFailed -> refresh(event, created)

            DavResult.NotFound, DavResult.HttpError(GONE) -> {
                merger.gone(event.accountId, event.href)
                ExecutionResult.Done
            }

            else -> failure(result)
        }
    }

    /** What the server accepted becomes the base; changes made while uploading stay dirty. */
    private suspend fun saved(
        sent: DavEventEntity,
        ics: String,
        etag: String?,
        conflicted: Set<EventField>
    ): ExecutionResult {
        val current = events.get(sent.id) ?: return ExecutionResult.Done
        val unchanged = StoredSeries.read(current) == StoredSeries.read(sent)
        val written = ServerEvent.read(ics, current.calendarId, current.id, clock.zone)
        events.update(
            current.copy(
                ics = ics,
                etag = etag,
                sequence = written?.event?.sequence ?: current.sequence,
                dirtyFields = if (unchanged) EventField.toBits(conflicted) else current.dirtyFields
            )
        )
        return ExecutionResult.Done
    }

    private fun conflictedFields(event: DavEventEntity): Set<EventField> = buildSet {
        if (event.conflictTitle != null) add(EventField.TITLE)
        if (event.conflictDescription != null) add(EventField.DESCRIPTION)
        if (event.conflictLocation != null) add(EventField.LOCATION)
    }

    /** The server copy changed: merge it in, then send the result on the next run. */
    private suspend fun refresh(event: DavEventEntity, created: Boolean): ExecutionResult {
        val calendar = calendars.get(event.calendarId) ?: return ExecutionResult.Done
        return when (val fetched = dav.read.fetch(calendar.href, listOf(event.href))) {
            is DavResult.Success -> {
                val resource = fetched.value.firstOrNull()
                if (resource == null) {
                    merger.gone(event.accountId, event.href)
                    ExecutionResult.Done
                } else {
                    merger.apply(event.accountId, calendar, resource)
                    if (created) ExecutionResult.Done else ExecutionResult.Retry(CONFLICT)
                }
            }

            else -> failure(fetched)
        }
    }

    /** The user wants it gone: delete whatever version the server has. */
    private suspend fun delete(
        eventId: Long,
        operation: QueuedOperation.DeleteEvent
    ): ExecutionResult = when (val result = dav.write.delete(operation.href, null)) {
        is DavResult.Success, DavResult.NotFound, DavResult.HttpError(GONE) -> {
            events.delete(eventId)
            ExecutionResult.Done
        }

        else -> failure(result)
    }

    private fun failure(result: DavResult<*>): ExecutionResult = when (result) {
        DavResult.Forbidden -> ExecutionResult.Failed(READ_ONLY)

        is DavResult.HttpError -> if (result.code < SERVER_ERRORS) {
            ExecutionResult.Failed("HTTP ${result.code}")
        } else {
            ExecutionResult.Retry("HTTP ${result.code}")
        }

        else -> ExecutionResult.Retry(result.toString())
    }

    private companion object {
        const val CONFLICT = "Changed on the server"
        const val READ_ONLY = "Read-only calendar"
        const val SERVER_ERRORS = 500
        const val GONE = 410
    }
}
