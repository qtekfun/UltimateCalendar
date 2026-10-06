// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import com.qtekfun.ultimatecalendar.data.local.StoredSeries
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.entity.DavCalendarEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavEventEntity
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavResource
import com.qtekfun.ultimatecalendar.sync.conflict.ConflictResolver
import com.qtekfun.ultimatecalendar.sync.conflict.EventField
import com.qtekfun.ultimatecalendar.sync.conflict.LocalVersion
import com.qtekfun.ultimatecalendar.sync.conflict.Resolution
import com.qtekfun.ultimatecalendar.sync.conflict.ServerVersion
import com.qtekfun.ultimatecalendar.sync.queue.OperationQueue
import com.qtekfun.ultimatecalendar.sync.queue.QueuedOperation
import java.time.Instant
import java.time.ZoneId

/**
 * Brings one server event into Room. Events without local changes simply take the server
 * version; changed ones go through [ConflictResolver] (SPEC §5). A local change is an event with
 * `dirtyFields`: whoever edits an event must set them and queue the operation.
 */
class EventMerger(
    database: UltimateCalendarDatabase,
    private val queue: OperationQueue,
    private val floating: ZoneId
) {
    private val events = database.davEventDao()
    private val operations = database.pendingOperationDao()

    /** Applies [resource] (with its data) of [calendar]; resources without events are ignored. */
    suspend fun apply(accountId: Long, calendar: DavCalendarEntity, resource: DavResource) {
        val ics = resource.data ?: return
        val local = events.byHref(accountId, resource.href)
        val server = ServerEvent.read(ics, calendar.id, local?.id ?: 0, floating) ?: return
        val modifiedAt = server.modifiedAt?.toEpochMilli()
        when {
            local == null -> events.insert(
                StoredSeries.create(accountId, calendar.id, resource.href, server.event)
                    .copy(ics = ics, etag = resource.etag, modifiedAt = modifiedAt)
            )

            // Deleted here: the queued delete wins, whatever the server changed.
            local.deleted -> Unit

            local.dirtyFields == 0 -> events.update(
                StoredSeries.write(local, server.event)
                    .copy(ics = ics, etag = resource.etag, modifiedAt = modifiedAt)
            )

            else -> merge(local, server, ics, resource.etag)
        }
    }

    /** The server no longer has [href]. */
    suspend fun gone(accountId: Long, href: String) {
        val local = events.byHref(accountId, href) ?: return
        val resolution = ConflictResolver.resolve(
            null,
            LocalVersion(StoredSeries.read(local), EventField.fromBits(local.dirtyFields), null),
            ServerVersion(null, null)
        )
        operations.deleteForEvent(accountId, local.id)
        if (local.deleted || resolution == Resolution.DeleteLocally) {
            events.delete(local.id)
        } else {
            // Kept with its local changes until the user keeps a copy or discards it.
            events.update(local.copy(deletedOnServer = true, etag = null, ics = null))
        }
    }

    private suspend fun merge(
        local: DavEventEntity,
        server: ServerEvent,
        ics: String,
        etag: String?
    ) {
        val base = local.ics?.let { ServerEvent.read(it, local.calendarId, local.id, floating) }
        val resolution = ConflictResolver.resolve(
            base?.event,
            LocalVersion(
                StoredSeries.read(local),
                EventField.fromBits(local.dirtyFields),
                local.modifiedAt?.let(Instant::ofEpochMilli)
            ),
            ServerVersion(server.event, server.modifiedAt)
        ) as Resolution.Merge
        val conflicts = resolution.conflicts.associateBy { it.field }
        val dirty = resolution.push + conflicts.keys
        events.update(
            StoredSeries.write(local, resolution.event).copy(
                ics = ics,
                etag = etag,
                // What is still to send keeps the time of the local change.
                modifiedAt = if (dirty.isEmpty()) {
                    server.modifiedAt?.toEpochMilli()
                } else {
                    local.modifiedAt
                },
                dirtyFields = EventField.toBits(dirty),
                // A server text that was emptied is "", so that it still tells of a conflict.
                conflictTitle = conflicts[EventField.TITLE]?.let { it.server.orEmpty() },
                conflictDescription = conflicts[EventField.DESCRIPTION]?.let {
                    it.server.orEmpty()
                },
                conflictLocation = conflicts[EventField.LOCATION]?.let { it.server.orEmpty() }
            )
        )
        if (resolution.push.isNotEmpty()) {
            queue.enqueue(local.accountId, local.id, QueuedOperation.UpdateEvent)
        }
    }
}
