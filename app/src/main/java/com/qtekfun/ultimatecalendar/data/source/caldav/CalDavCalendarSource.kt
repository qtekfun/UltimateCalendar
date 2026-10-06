// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.caldav

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteException
import com.qtekfun.ultimatecalendar.data.ical.EventStatus
import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.data.local.StoredKey
import com.qtekfun.ultimatecalendar.data.local.StoredSeries
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavCalendarEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavEventEntity
import com.qtekfun.ultimatecalendar.data.source.CalDavIds
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.provider.Abort
import com.qtekfun.ultimatecalendar.data.source.provider.abort
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.recurrence.EventSeries
import com.qtekfun.ultimatecalendar.domain.recurrence.Expansion
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceEngine
import com.qtekfun.ultimatecalendar.domain.recurrence.SeriesInstances
import com.qtekfun.ultimatecalendar.domain.recurrence.key
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.search.SearchMatcher
import com.qtekfun.ultimatecalendar.domain.search.SearchQuery
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import com.qtekfun.ultimatecalendar.domain.search.SqlLike
import com.qtekfun.ultimatecalendar.sync.conflict.EventField
import com.qtekfun.ultimatecalendar.sync.queue.OperationQueue
import com.qtekfun.ultimatecalendar.sync.queue.QueuedOperation
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

/**
 * The account's own CalDAV calendars (RF-12) as a [CalendarSource]. Room is the source of truth:
 * reads never touch the network, and every write is local first. A write changes the stored
 * event, marks the fields it changed and when (what the conflict resolver needs, SPEC §5) and
 * queues the operation; the sync engine sends it when there is a connection. Repetitions are
 * expanded by the `RecurrenceEngine` over the stored series. The ids are the Room rows with the
 * bit [CalDavIds] sets, so that they never collide with the provider's.
 *
 * Invitations use the server's scheduling (RFC 6638): the app only writes the `ORGANIZER` and
 * `ATTENDEE` lines, and the server mails the guests; an answer is the user's own `PARTSTAT`.
 */
@Singleton
@Suppress("TooManyFunctions", "LongParameterList")
class CalDavCalendarSource @Inject constructor(
    private val accounts: CalDavAccounts,
    private val database: UltimateCalendarDatabase,
    private val queue: OperationQueue,
    private val clock: Clock,
    private val sync: CalDavSyncTrigger,
    private val uids: UidFactory,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : CalendarSource {
    private val calendarRows = database.davCalendarDao()
    private val eventRows = database.davEventDao()

    /**
     * Local writes announce themselves at once; Room's invalidation, which also sees what the
     * sync stores, may take a moment to subscribe, so it alone could miss a change made right
     * after the collector started.
     */
    private val written = MutableSharedFlow<Unit>(
        extraBufferCapacity = WRITES_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val changes: Flow<Unit> = merge(
        written,
        database.invalidationTracker
            .createFlow("dav_event", "dav_calendar", emitInitialState = false)
            .map { }
    )

    override suspend fun calendars(): CalendarResult<List<CalendarInfo>> = guarded {
        val account = accounts.current() ?: return@guarded emptyList()
        calendarRows.all(account.id)
            .sortedWith(
                compareBy({
                    it.sortOrder == null
                }, { it.sortOrder }, { it.name.lowercase() })
            )
            .map { CalDavMapping.calendar(it, account) }
    }

    override suspend fun instances(
        range: TimeRange,
        calendarIds: Set<CalendarId>?
    ): CalendarResult<List<EventInstance>> = guarded {
        val account = accounts.current() ?: return@guarded emptyList()
        val scope = scope(account, calendarIds)
        if (scope.isEmpty()) return@guarded emptyList()
        val me = CalDavMapping.addresses(account)
        eventRows.inWindow(
            scope.map {
                it.id
            },
            range.start.toEpochMilli(),
            range.end.toEpochMilli()
        )
            .filter { it.status != EventStatus.CANCELLED }
            .flatMap { instancesOf(it, range, me) }
            .sortedWith(compareBy({ it.time.startIn(ZoneOffset.UTC) }, { it.eventId.value }))
    }

    override suspend fun search(
        query: String,
        calendarIds: Set<CalendarId>?,
        range: TimeRange?
    ): CalendarResult<List<SearchableEvent>> = guarded {
        val words = SearchQuery.of(query)
        val account = accounts.current()
        val scope = account?.let { scope(it, calendarIds) }.orEmpty()
        if (words.isBlank || scope.isEmpty()) return@guarded emptyList()
        val pattern = SqlLike.contains(SqlLike.mostSelective(words.words))
        eventRows.matching(scope.map { it.id }, pattern)
            .filter { it.status != EventStatus.CANCELLED }
            .map { StoredSeries.read(it).series }
            .filter { range == null || occursIn(it, range) }
            .map { SearchableEvent.of(it.event) }
            .filter { SearchMatcher.match(words, it) != null }
            .map(CalDavMapping::searchable)
    }

    override suspend fun event(id: EventId): CalendarResult<Event> = guarded {
        val account = requireAccount()
        CalDavMapping.event(StoredSeries.read(row(account, id)).series.event)
    }

    override suspend fun create(draft: EventDraft): CalendarResult<EventId> = guarded {
        val account = requireAccount()
        val calendar = writableCalendar(account, draft.calendarId)
        val uid = uids.next()
        val href = calendar.href.trimEnd('/') + "/" + uid + ".ics"
        val event = CalDavEdits.created(draft, calendar.id, uid, CalDavMapping.addresses(account))
        val row = StoredSeries.create(account.id, calendar.id, href, event).copy(
            color = draft.color,
            // Nothing of it is on the server yet.
            dirtyFields = ALL_FIELDS,
            modifiedAt = clock.millis()
        )
        val id = inTransaction {
            eventRows.insert(row).also {
                queue.enqueue(account.id, it, QueuedOperation.CreateEvent)
            }
        }
        committed()
        CalDavIds.event(id)
    }

    override suspend fun update(event: Event): CalendarResult<Unit> = guarded {
        val account = requireAccount()
        val stored = row(account, event.id)
        if (CalDavIds.rowOf(event.calendarId) != stored.calendarId) {
            abort(CalendarError.Invalid("an event cannot change source or account"))
        }
        writableCalendar(account, CalDavIds.calendar(stored.calendarId))
        val before = StoredSeries.read(stored)
        val after = CalDavEdits.replaced(before, event, CalDavMapping.addresses(account))
        val fields = CalDavEdits.changed(before, after)
        if (fields.isNotEmpty() || event.color != stored.color) {
            save(
                account,
                stored.copy(color = event.color),
                after,
                fields,
                QueuedOperation.UpdateEvent
            )
        }
    }

    override suspend fun delete(id: EventId): CalendarResult<Unit> = guarded {
        val account = requireAccount()
        val stored = row(account, id)
        writableCalendar(account, CalDavIds.calendar(stored.calendarId))
        inTransaction {
            val sent = queue.enqueue(
                account.id,
                stored.id,
                QueuedOperation.DeleteEvent(stored.href)
            )
            if (sent) {
                eventRows.update(stored.copy(deleted = true, modifiedAt = clock.millis()))
            } else {
                // The server never heard of it: nothing to tell it.
                eventRows.delete(stored.id)
            }
        }
        committed()
    }

    override suspend fun editInstance(
        id: EventId,
        originalStart: Instant,
        changes: EventDraft
    ): CalendarResult<Unit> = change(id, originalStart, changes)

    override suspend fun cancelInstance(id: EventId, originalStart: Instant): CalendarResult<Unit> =
        change(id, originalStart, null)

    override suspend fun respond(id: EventId, status: AttendeeStatus): CalendarResult<Unit> =
        guarded {
            val account = requireAccount()
            val stored = row(account, id)
            val calendar = calendarOf(account, stored)
            if (!CalDavMapping.calendar(
                    calendar,
                    account
                ).access.canRespond
            ) {
                abort(CalendarError.ReadOnly)
            }
            val before = StoredSeries.read(stored)
            val answer = CalDavEdits.answered(before, CalDavMapping.addresses(account), status)
                ?: abort(CalendarError.Invalid("not an attendee"))
            val fields = CalDavEdits.changed(before, answer.event)
            // The queue applies the answer again before each upload, so a change made on the server
            // meanwhile does not undo it. The upload is the user's own PARTSTAT: a server that
            // schedules tells the organizer, one that does not just keeps the answer.
            if (before.series != answer.event.series) {
                val operation = QueuedOperation.Respond(answer.email, status, null)
                save(account, stored, answer.event, fields, operation)
            }
        }

    private suspend fun change(
        id: EventId,
        originalStart: Instant,
        edit: EventDraft?
    ): CalendarResult<Unit> = guarded {
        val account = requireAccount()
        val stored = row(account, id)
        writableCalendar(account, CalDavIds.calendar(stored.calendarId))
        val before = StoredSeries.read(stored)
        if (!CalDavEdits.repeats(before.series)) abort(CalendarError.Invalid("not a series"))
        val key = CalDavEdits.keyOf(before.series, originalStart)
        if (!hasOccurrence(before.series, key)) abort(CalendarError.NotFound)
        val after = CalDavEdits.overridden(before, key, edit)
        val stamp = StoredKey.of(key)
        val operation = if (edit == null) {
            QueuedOperation.CancelInstance(stamp)
        } else {
            QueuedOperation.EditInstance(stamp)
        }
        save(account, stored, after, setOf(EventField.OVERRIDES), operation)
    }

    /** Stores [after] in [stored]'s row with its changes marked, and queues [operation]. */
    private suspend fun save(
        account: DavAccountEntity,
        stored: DavEventEntity,
        after: IcsEvent,
        fields: Set<EventField>,
        operation: QueuedOperation
    ) {
        val updated = StoredSeries.write(stored, after).copy(
            dirtyFields = stored.dirtyFields or EventField.toBits(fields),
            modifiedAt = clock.millis()
        )
        inTransaction {
            eventRows.update(updated)
            queue.enqueue(account.id, stored.id, operation)
        }
        committed()
    }

    private fun instancesOf(
        row: DavEventEntity,
        range: TimeRange,
        me: List<String>
    ): List<EventInstance> {
        val series = StoredSeries.read(row).series
        return expand(series, range).map { instance ->
            val attendees = CalDavEdits.attendeesAt(series, instance.time)
            CalDavMapping.instance(
                instance.copy(
                    selfStatus = attendees.firstOrNull { it.isOneOf(me) }?.status,
                    hasAttendees = attendees.isNotEmpty()
                )
            )
        }
    }

    /**
     * The instances of [series] in [range]. A rule the engine cannot read shows the series'
     * first occurrence only, rather than hiding the event.
     */
    private fun expand(series: EventSeries, range: TimeRange): List<EventInstance> =
        when (val expansion = RecurrenceEngine.expand(series, range, ZoneOffset.UTC)) {
            is Expansion.Complete -> expansion.instances

            is Expansion.LimitReached -> expansion.instances

            is Expansion.Unsupported ->
                SeriesInstances.assemble(series, listOf(series.event.time), range, ZoneOffset.UTC)
        }

    private fun occursIn(series: EventSeries, range: TimeRange) = expand(series, range).isNotEmpty()

    /** Whether [key] is an occurrence of [series]: generated by the rule, or already changed. */
    private fun hasOccurrence(series: EventSeries, key: OccurrenceKey): Boolean {
        if (series.overrides.any { it.recurrenceId == key }) return true
        val start = when (key) {
            is OccurrenceKey.Moment -> key.at
            is OccurrenceKey.Day -> key.date.atStartOfDay(ZoneOffset.UTC).toInstant()
        }
        val probe = TimeRange(start, start.plus(PROBE))
        return expand(series, probe).any { it.time.key() == key }
    }

    private suspend fun scope(
        account: DavAccountEntity,
        calendarIds: Set<CalendarId>?
    ): List<DavCalendarEntity> {
        val all = calendarRows.all(account.id)
        return if (calendarIds == null) {
            all
        } else {
            val wanted = calendarIds.filter(CalDavIds::isCalDav).map(CalDavIds::rowOf).toSet()
            all.filter { it.id in wanted }
        }
    }

    private suspend fun requireAccount(): DavAccountEntity =
        accounts.current() ?: abort(CalendarError.NotFound)

    /** The event row [id], if it is of this account and not deleted here. */
    private suspend fun row(account: DavAccountEntity, id: EventId): DavEventEntity {
        if (!CalDavIds.isCalDav(id)) abort(CalendarError.NotFound)
        val found = eventRows.get(CalDavIds.rowOf(id))
        if (found == null || found.accountId != account.id || found.deleted) {
            abort(CalendarError.NotFound)
        }
        return found
    }

    private suspend fun calendarOf(account: DavAccountEntity, row: DavEventEntity) =
        calendarRows.get(row.calendarId)?.takeIf { it.accountId == account.id }
            ?: abort(CalendarError.NotFound)

    private suspend fun writableCalendar(
        account: DavAccountEntity,
        id: CalendarId
    ): DavCalendarEntity {
        if (!CalDavIds.isCalDav(id)) abort(CalendarError.NotFound)
        val calendar = calendarRows.get(CalDavIds.rowOf(id))?.takeIf { it.accountId == account.id }
            ?: abort(CalendarError.NotFound)
        if (!calendar.writable) abort(CalendarError.ReadOnly)
        return calendar
    }

    /** A change is stored: tell the readers and ask for a sync soon. */
    private fun committed() {
        written.tryEmit(Unit)
        sync.localChange()
    }

    private suspend fun <R> inTransaction(block: suspend () -> R): R =
        database.useWriterConnection { transactor -> transactor.immediateTransaction { block() } }

    private suspend fun <T> guarded(block: suspend () -> T): CalendarResult<T> =
        withContext(dispatcher) {
            try {
                CalendarResult.Success(block())
            } catch (stop: Abort) {
                CalendarResult.Failure(stop.error)
            } catch (_: SQLiteException) {
                CalendarResult.Failure(CalendarError.SourceFailure("database"))
            } catch (_: IllegalArgumentException) {
                CalendarResult.Failure(CalendarError.Invalid("the event could not be stored"))
            }
        }

    private companion object {
        val ALL_FIELDS = EventField.toBits(EventField.entries.toSet())
        const val WRITES_BUFFER = 8
        val PROBE: Duration = Duration.ofSeconds(1)
    }
}
