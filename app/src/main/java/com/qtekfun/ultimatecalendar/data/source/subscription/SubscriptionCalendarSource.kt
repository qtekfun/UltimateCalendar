// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.subscription

import androidx.sqlite.SQLiteException
import com.qtekfun.ultimatecalendar.data.ical.EventStatus
import com.qtekfun.ultimatecalendar.data.local.StoredSubscriptionEvents
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.entity.SubscriptionEntity
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.SeriesExpander
import com.qtekfun.ultimatecalendar.data.source.SubscriptionIds
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
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.search.SearchMatcher
import com.qtekfun.ultimatecalendar.domain.search.SearchQuery
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import com.qtekfun.ultimatecalendar.domain.search.SqlLike
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The calendars the user subscribed to by address (T39) as a read-only [CalendarSource]. Room
 * holds the events the last download read; nothing here touches the network. Repetitions are
 * expanded by the `RecurrenceEngine` over the stored series, as for CalDAV. Every write fails with
 * [CalendarError.ReadOnly], the calendars have [com.qtekfun.ultimatecalendar.domain.model.CalendarAccess.READ]
 * and no instance has attendees or an answer, so a subscription never produces an invitation.
 * It never reminds either: the events carry no reminders (`SubscriptionFeed` drops them).
 * A disabled subscription is invisible everywhere but keeps its events for when it is enabled.
 * The ids are the Room rows with the bit [SubscriptionIds] sets.
 */
@Singleton
@Suppress("TooManyFunctions")
class SubscriptionCalendarSource @Inject constructor(
    private val database: UltimateCalendarDatabase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : CalendarSource {
    private val subscriptions = database.subscriptionDao()
    private val events = database.subscriptionEventDao()

    override val changes: Flow<Unit> = database.invalidationTracker
        .createFlow("subscription", "subscription_event", emitInitialState = false)
        .map { }

    override suspend fun calendars(): CalendarResult<List<CalendarInfo>> = guarded {
        enabled().map(SubscriptionMapping::calendar)
    }

    override suspend fun instances(
        range: TimeRange,
        calendarIds: Set<CalendarId>?
    ): CalendarResult<List<EventInstance>> = guarded {
        val scope = scope(calendarIds)
        if (scope.isEmpty()) return@guarded emptyList()
        events.inWindow(scope, range.start.toEpochMilli(), range.end.toEpochMilli())
            .filter { it.status != EventStatus.CANCELLED }
            .flatMap { row ->
                SeriesExpander.expand(StoredSubscriptionEvents.read(row).series, range)
                    .map(SubscriptionMapping::instance)
            }
            .sortedWith(compareBy({ it.time.startIn(ZoneOffset.UTC) }, { it.eventId.value }))
    }

    override suspend fun search(
        query: String,
        calendarIds: Set<CalendarId>?,
        range: TimeRange?
    ): CalendarResult<List<SearchableEvent>> = guarded {
        val words = SearchQuery.of(query)
        val scope = scope(calendarIds)
        if (words.isBlank || scope.isEmpty()) return@guarded emptyList()
        val pattern = SqlLike.contains(SqlLike.mostSelective(words.words))
        events.matching(scope, pattern)
            .filter { it.status != EventStatus.CANCELLED }
            .map { StoredSubscriptionEvents.read(it).series }
            .filter { range == null || SeriesExpander.occursIn(it, range) }
            .map { SearchableEvent.of(it.event) }
            .filter { SearchMatcher.match(words, it) != null }
            .map(SubscriptionMapping::searchable)
    }

    override suspend fun event(id: EventId): CalendarResult<Event> = guarded {
        if (!SubscriptionIds.isSubscription(id)) abort(CalendarError.NotFound)
        val row = events.get(SubscriptionIds.rowOf(id)) ?: abort(CalendarError.NotFound)
        if (subscriptions.get(row.subscriptionId)?.enabled != true) abort(CalendarError.NotFound)
        SubscriptionMapping.event(StoredSubscriptionEvents.read(row).series.event)
    }

    override suspend fun create(draft: EventDraft): CalendarResult<EventId> = readOnly()

    override suspend fun update(event: Event): CalendarResult<Unit> = readOnly()

    override suspend fun delete(id: EventId): CalendarResult<Unit> = readOnly()

    override suspend fun editInstance(
        id: EventId,
        originalStart: Instant,
        changes: EventDraft
    ): CalendarResult<Unit> = readOnly()

    override suspend fun cancelInstance(id: EventId, originalStart: Instant): CalendarResult<Unit> =
        readOnly()

    /** There is nothing to answer: a subscription's events have no attendees. */
    override suspend fun respond(id: EventId, status: AttendeeStatus): CalendarResult<Unit> =
        readOnly()

    private fun readOnly(): CalendarResult.Failure = CalendarResult.Failure(CalendarError.ReadOnly)

    private suspend fun enabled(): List<SubscriptionEntity> = subscriptions.all().filter {
        it.enabled
    }

    /** The rows of the enabled subscriptions among [calendarIds] (all of them when null). */
    private suspend fun scope(calendarIds: Set<CalendarId>?): List<Long> {
        val wanted = calendarIds?.filter(SubscriptionIds::isSubscription)
            ?.map(SubscriptionIds::rowOf)?.toSet()
        return enabled().map { it.id }.filter { wanted == null || it in wanted }
    }

    private suspend fun <T> guarded(block: suspend () -> T): CalendarResult<T> =
        withContext(dispatcher) {
            try {
                CalendarResult.Success(block())
            } catch (stop: Abort) {
                CalendarResult.Failure(stop.error)
            } catch (_: SQLiteException) {
                CalendarResult.Failure(CalendarError.SourceFailure("database"))
            }
        }
}
