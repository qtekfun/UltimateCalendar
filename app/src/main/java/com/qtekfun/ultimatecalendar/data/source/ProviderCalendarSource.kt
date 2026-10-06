// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import com.qtekfun.ultimatecalendar.data.source.provider.Abort
import com.qtekfun.ultimatecalendar.data.source.provider.AttendeeMapping
import com.qtekfun.ultimatecalendar.data.source.provider.ChildOps
import com.qtekfun.ultimatecalendar.data.source.provider.EventMapping
import com.qtekfun.ultimatecalendar.data.source.provider.InstanceMapping
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderFailure
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderGateway
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderOp
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderQuery
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderSearch
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderStore
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderTable
import com.qtekfun.ultimatecalendar.data.source.provider.SeriesExceptions
import com.qtekfun.ultimatecalendar.data.source.provider.abort
import com.qtekfun.ultimatecalendar.data.source.provider.long
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.search.SearchMatcher
import com.qtekfun.ultimatecalendar.domain.search.SearchQuery
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The Android calendar provider (`CalendarContract`) as a [CalendarSource]: Google, DAVx5 and
 * any other account the phone syncs, read and written as a normal client. Ranges come from
 * `Instances` (repetitions are never expanded here), series exceptions go through
 * `CONTENT_EXCEPTION_URI`, and sync columns and other apps' extended properties are left alone.
 * All provider access runs on [dispatcher]; failures come back as [CalendarResult]s.
 *
 * Choices the provider forces: an occurrence that was changed on its own is a separate event in
 * the provider, and its instance is reported under the id of its series; a user answer is
 * written to the attendee row of the calendar's owner account; the organizer of a new event is
 * the calendar's owner, without making the owner an attendee.
 */
@Singleton
class ProviderCalendarSource @Inject constructor(
    private val gateway: ProviderGateway,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : CalendarSource {
    private val store = ProviderStore(gateway)
    private val exceptions = SeriesExceptions(store)
    private val search = ProviderSearch(gateway)

    override val changes: Flow<Unit> get() = gateway.changes

    override suspend fun calendars(): CalendarResult<List<CalendarInfo>> = guarded {
        store.calendars()
    }

    override suspend fun instances(
        range: TimeRange,
        calendarIds: Set<CalendarId>?
    ): CalendarResult<List<EventInstance>> = guarded {
        val query = ProviderQuery(
            table = ProviderTable.INSTANCES,
            projection = InstanceMapping.projection,
            rangeMs = range.start.toEpochMilli()..range.end.toEpochMilli()
        )
        gateway.query(query)
            .mapNotNull { InstanceMapping.toInstance(it, range) }
            .filter { calendarIds == null || it.calendarId in calendarIds }
            .sortedBy { it.time.startIn(ZoneOffset.UTC) }
    }

    override suspend fun search(
        query: String,
        calendarIds: Set<CalendarId>?,
        range: TimeRange?
    ): CalendarResult<List<SearchableEvent>> = guarded {
        val words = SearchQuery.of(query)
        if (words.isBlank || calendarIds?.isEmpty() == true) {
            emptyList()
        } else {
            val inRange = range?.let { search.eventsInRange(it) }
            search.candidates(words, calendarIds)
                .filter { inRange == null || it.eventId in inRange }
                .filter { SearchMatcher.match(words, it) != null }
        }
    }

    override suspend fun event(id: EventId): CalendarResult<Event> = guarded {
        store.event(store.eventRow(id))
    }

    override suspend fun create(draft: EventDraft): CalendarResult<EventId> = guarded {
        val calendar = store.calendar(draft.calendarId)
        if (!calendar.access.canCreate) abort(CalendarError.ReadOnly)
        val values = EventMapping.toValues(draft) + mapOf(
            Events.CALENDAR_ID to calendar.id.value,
            Events.ORGANIZER to calendar.ownerEmail
        )
        val ops = listOf<ProviderOp>(ProviderOp.Insert(ProviderTable.EVENTS, values)) +
            ChildOps.inserts(draft.attendees, draft.reminders, eventId = null, parentOp = 0)
        EventId(store.insert(ops).first() ?: abort(CalendarError.SourceFailure("no event id")))
    }

    override suspend fun update(event: Event): CalendarResult<Unit> = guarded {
        val row = store.eventRow(event.id)
        store.editableCalendar(row)
        val stored = store.event(row)
        val values = EventMapping.toValues(EventMapping.draftOf(event))
        // Attendees and reminders are only replaced when they differ: rewriting them needlessly
        // would mark them changed for the sync adapter.
        val attendees = event.attendees.takeIf { it.toSet() != stored.attendees.toSet() }
        val reminders = event.reminders.takeIf { it.toSet() != stored.reminders.toSet() }
        store.write(
            listOf<ProviderOp>(ProviderOp.Update(ProviderTable.EVENTS, event.id.value, values)) +
                ChildOps.replace(event.id.value, attendees, reminders)
        )
    }

    override suspend fun delete(id: EventId): CalendarResult<Unit> = guarded {
        store.editableCalendar(store.eventRow(id))
        store.write(listOf(ProviderOp.Delete(ProviderTable.EVENTS, id = id.value)))
    }

    override suspend fun editInstance(
        id: EventId,
        originalStart: Instant,
        changes: EventDraft
    ): CalendarResult<Unit> = guarded { exceptions.change(id, originalStart, changes) }

    override suspend fun cancelInstance(id: EventId, originalStart: Instant): CalendarResult<Unit> =
        guarded { exceptions.change(id, originalStart, null) }

    override suspend fun respond(id: EventId, status: AttendeeStatus): CalendarResult<Unit> =
        guarded {
            val row = store.eventRow(id)
            val calendar = store.calendar(requireNotNull(EventMapping.calendarOf(row)))
            if (!calendar.access.canRespond) abort(CalendarError.ReadOnly)
            val me = listOfNotNull(calendar.ownerEmail)
            val mine = store.children(ProviderTable.ATTENDEES, AttendeeMapping.projection, id.value)
                .firstOrNull { AttendeeMapping.toAttendee(it)?.isOneOf(me) == true }
                ?: abort(CalendarError.Invalid("not an attendee"))
            val answer = mapOf(Attendees.ATTENDEE_STATUS to AttendeeMapping.statusCode(status))
            val attendeeId = requireNotNull(mine.long(Attendees._ID))
            store.write(listOf(ProviderOp.Update(ProviderTable.ATTENDEES, attendeeId, answer)))
        }

    /** Runs [block] on the IO dispatcher, turning failures into [CalendarResult.Failure]. */
    private suspend fun <T> guarded(block: () -> T): CalendarResult<T> = withContext(dispatcher) {
        try {
            CalendarResult.Success(block())
        } catch (stop: Abort) {
            CalendarResult.Failure(stop.error)
        } catch (_: SecurityException) {
            CalendarResult.Failure(CalendarError.PermissionDenied)
        } catch (failure: ProviderFailure) {
            CalendarResult.Failure(CalendarError.SourceFailure(failure.reason))
        } catch (_: IllegalArgumentException) {
            CalendarResult.Failure(CalendarError.Invalid("the provider rejected the data"))
        }
    }
}
