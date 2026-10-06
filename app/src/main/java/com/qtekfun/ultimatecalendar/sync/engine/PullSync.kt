// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavCalendarEntity
import com.qtekfun.ultimatecalendar.data.remote.caldav.CalDav
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavChanges
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavCollection
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavResource
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavResult
import com.qtekfun.ultimatecalendar.data.remote.caldav.then
import com.qtekfun.ultimatecalendar.sync.queue.OperationQueue
import java.time.Clock
import javax.inject.Inject

/**
 * Brings the server state into Room: the account's calendars, then the events of each calendar
 * that changed since its sync token (or, without sync tokens, since its ctag). Each calendar is
 * written in one transaction, so a pull cut off halfway leaves every calendar either as before
 * or fully updated.
 */
class PullSync @Inject constructor(
    private val database: UltimateCalendarDatabase,
    queue: OperationQueue,
    clock: Clock
) {
    private val accounts = database.davAccountDao()
    private val calendars = database.davCalendarDao()
    private val events = database.davEventDao()
    private val merger = EventMerger(database, queue, clock.zone)

    /** Pulls everything; returns the first failure, or null when all is up to date. */
    suspend fun pull(dav: CalDav, account: DavAccountEntity): DavResult<*>? {
        val result = home(dav, account)
            .then { home -> dav.read.calendars(home) }
            .then { collections -> DavResult.Success(saveCalendars(account.id, collections)) }
        if (result !is DavResult.Success) return result
        return result.value.firstNotNullOfOrNull { (calendar, ctag) ->
            pullCalendar(dav, account.id, calendar, ctag)
        }
    }

    private suspend fun home(dav: CalDav, account: DavAccountEntity): DavResult<String> =
        account.calendarHome?.let { DavResult.Success(it) } ?: dav.read.profile().then { found ->
            accounts.update(
                account.copy(
                    calendarHome = found.home,
                    userAddresses = found.addresses.joinToString(","),
                    scheduling = found.schedules
                )
            )
            DavResult.Success(found.home)
        }

    /**
     * Saves the server calendars, keeping what only lives here (the last sync token and ctag),
     * and pairs each with the ctag the server has now. Calendars gone from the server go with
     * their events, unless one has unsent changes.
     */
    private suspend fun saveCalendars(
        accountId: Long,
        collections: List<DavCollection>
    ): List<Pair<DavCalendarEntity, String?>> {
        val known = calendars.all(accountId).associateBy { it.href }
        val saved = collections.map { collection ->
            val old = known[collection.href]
            calendars.upsert(
                DavCalendarEntity(
                    accountId = accountId,
                    href = collection.href,
                    name = collection.name,
                    color = collection.color,
                    sortOrder = collection.order,
                    writable = collection.writable,
                    syncToken = old?.syncToken,
                    // The ctag of the last pull; the server's current one is compared with it.
                    ctag = old?.ctag
                )
            ) to collection.ctag
        }
        val onServer = collections.map { it.href }.toSet()
        known.values.filter { it.href !in onServer }
            .filter { gone ->
                events.inCalendar(gone.id).none {
                    it.dirtyFields != 0 ||
                        it.etag == null
                }
            }
            .forEach { calendars.delete(it.id) }
        return saved
    }

    /**
     * Pulls one calendar: with its sync token when the server supports sync-collection, otherwise
     * (as for the calendars Deck publishes) every event with its ETag, and only when [ctag]
     * changed.
     */
    private suspend fun pullCalendar(
        dav: CalDav,
        accountId: Long,
        calendar: DavCalendarEntity,
        ctag: String?
    ): DavResult<*>? {
        if (calendar.syncToken == null && ctag != null && ctag == calendar.ctag) return null
        var full = calendar.syncToken == null
        var changes = dav.read.changes(calendar.href, calendar.syncToken)
        if (changes == DavResult.SyncTokenExpired) {
            full = true
            changes = dav.read.changes(calendar.href, null)
        }
        if (changes == DavResult.HttpError(UNSUPPORTED_REPORT)) {
            full = true
            changes = dav.read.allEvents(calendar.href).then {
                DavResult.Success(DavChanges(it, emptyList(), null))
            }
        }
        return changes.then { found -> fetchChanged(dav, calendar, found) }
            .then { (found, fetched) ->
                save(accountId, calendar, found, fetched, full)
                calendars.setCtag(calendar.id, ctag)
                DavResult.Success(Unit)
            }.takeIf { it !is DavResult.Success }
    }

    /** Downloads the events whose ETag differs from the local one, a batch at a time. */
    private suspend fun fetchChanged(
        dav: CalDav,
        calendar: DavCalendarEntity,
        changes: DavChanges
    ): DavResult<Pair<DavChanges, List<DavResource>>> {
        val local = events.inCalendar(calendar.id).associateBy { it.href }
        val stale = changes.changed.filter {
            it.etag == null || local[it.href]?.etag != it.etag
        }.map { it.href }
        val fetched = mutableListOf<DavResource>()
        for (batch in stale.chunked(BATCH)) {
            when (val result = dav.read.fetch(calendar.href, batch)) {
                is DavResult.Success -> fetched += result.value
                else -> return result.then { error("unreachable") }
            }
        }
        return DavResult.Success(changes to fetched)
    }

    /** On a full pull, events the server did not list are gone too, unless never uploaded. */
    private suspend fun save(
        accountId: Long,
        calendar: DavCalendarEntity,
        changes: DavChanges,
        fetched: List<DavResource>,
        full: Boolean
    ) = inTransaction {
        fetched.forEach { merger.apply(accountId, calendar, it) }
        val listed = changes.changed.map { it.href }.toSet()
        val missing = if (full) {
            events.inCalendar(calendar.id).filter {
                it.etag != null && it.href !in listed
            }.map { it.href }
        } else {
            emptyList()
        }
        (changes.deleted + missing).forEach { merger.gone(accountId, it) }
        calendars.setSyncToken(calendar.id, changes.syncToken)
    }

    private suspend fun <R> inTransaction(block: suspend () -> R): R =
        database.useWriterConnection { transactor -> transactor.immediateTransaction { block() } }

    private companion object {
        /** Events per calendar-multiget request. */
        const val BATCH = 50

        /** Sabre's answer to a REPORT the collection does not support. */
        const val UNSUPPORTED_REPORT = 415
    }
}
