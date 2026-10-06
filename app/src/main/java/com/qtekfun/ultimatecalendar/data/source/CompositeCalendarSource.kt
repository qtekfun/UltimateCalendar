// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.reminders.EventReminders
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/**
 * The one [CalendarSource] the app uses: the Android provider's calendars, the app's own CalDAV
 * calendars and the read-only subscriptions side by side. Reads merge all three; a change goes to
 * the source the id belongs to (see [SourceKind]). With no CalDAV calendars and no subscriptions
 * the answers are the provider's, unchanged.
 *
 * A CalDAV or subscription failure never hides the provider's calendars, and a provider failure
 * other than a missing permission never hides theirs.
 */
@Suppress("TooManyFunctions")
class CompositeCalendarSource(
    private val provider: CalendarSource,
    private val caldav: CalendarSource,
    private val subscriptions: CalendarSource
) : CalendarSource {
    override val changes: Flow<Unit>
        get() = merge(provider.changes, caldav.changes, subscriptions.changes)

    override suspend fun calendars(): CalendarResult<List<CalendarInfo>> =
        merged(provider.calendars(), listOf(caldav.calendars(), subscriptions.calendars())) { it }

    override suspend fun instances(
        range: TimeRange,
        calendarIds: Set<CalendarId>?
    ): CalendarResult<List<EventInstance>> = merged(
        ask(calendarIds, SourceKind.PROVIDER) { provider.instances(range, it) },
        listOf(
            ask(calendarIds, SourceKind.CALDAV) { caldav.instances(range, it) },
            ask(calendarIds, SourceKind.SUBSCRIPTION) { subscriptions.instances(range, it) }
        )
    ) { list -> list.sortedBy { it.time.startIn(ZoneOffset.UTC) } }

    override suspend fun instancesWithReminders(
        range: TimeRange,
        calendarIds: Set<CalendarId>?
    ): CalendarResult<List<EventReminders>> = merged(
        ask(calendarIds, calDav = false) { provider.instancesWithReminders(range, it) },
        ask(calendarIds, calDav = true) { caldav.instancesWithReminders(range, it) }
    ) { list -> list.sortedBy { it.instance.time.startIn(ZoneOffset.UTC) } }

    override suspend fun search(
        query: String,
        calendarIds: Set<CalendarId>?,
        range: TimeRange?
    ): CalendarResult<List<SearchableEvent>> = merged(
        ask(calendarIds, SourceKind.PROVIDER) { provider.search(query, it, range) },
        listOf(
            ask(calendarIds, SourceKind.CALDAV) { caldav.search(query, it, range) },
            ask(calendarIds, SourceKind.SUBSCRIPTION) { subscriptions.search(query, it, range) }
        )
    ) { it }

    override suspend fun event(id: EventId): CalendarResult<Event> = owner(id).event(id)

    override suspend fun create(draft: EventDraft): CalendarResult<EventId> =
        owner(draft.calendarId).create(draft)

    override suspend fun update(event: Event): CalendarResult<Unit> =
        if (SourceKind.of(event.id) != SourceKind.of(event.calendarId)) {
            CalendarResult.Failure(CalendarError.Invalid("an event cannot change source"))
        } else {
            owner(event.id).update(event)
        }

    override suspend fun delete(id: EventId): CalendarResult<Unit> = owner(id).delete(id)

    override suspend fun editInstance(
        id: EventId,
        originalStart: Instant,
        changes: EventDraft
    ): CalendarResult<Unit> = owner(id).editInstance(id, originalStart, changes)

    override suspend fun cancelInstance(id: EventId, originalStart: Instant): CalendarResult<Unit> =
        owner(id).cancelInstance(id, originalStart)

    override suspend fun respond(id: EventId, status: AttendeeStatus): CalendarResult<Unit> =
        owner(id).respond(id, status)

    private fun owner(id: EventId) = owner(SourceKind.of(id))

    private fun owner(id: CalendarId) = owner(SourceKind.of(id))

    private fun owner(kind: SourceKind) = when (kind) {
        SourceKind.PROVIDER -> provider
        SourceKind.CALDAV -> caldav
        SourceKind.SUBSCRIPTION -> subscriptions
    }

    /**
     * Asks one source for the calendars of [calendarIds] that are its own (all of its calendars
     * when null). A source none of the calendars belong to is not asked.
     */
    private suspend fun <T> ask(
        calendarIds: Set<CalendarId>?,
        kind: SourceKind,
        call: suspend (Set<CalendarId>?) -> CalendarResult<List<T>>
    ): CalendarResult<List<T>> {
        val own = calendarIds?.filter { SourceKind.of(it) == kind }?.toSet()
        return if (own?.isEmpty() == true) CalendarResult.Success(emptyList()) else call(own)
    }

    /**
     * The answers as one. The provider's answer is returned as it is when the other sources have
     * nothing to add, so that without an account or a subscription nothing changes.
     */
    private fun <T> merged(
        fromProvider: CalendarResult<List<T>>,
        others: List<CalendarResult<List<T>>>,
        order: (List<T>) -> List<T>
    ): CalendarResult<List<T>> {
        val extra = others.flatMap { (it as? CalendarResult.Success)?.value.orEmpty() }
        return when {
            fromProvider is CalendarResult.Failure ->
                if (fromProvider.error != CalendarError.PermissionDenied && extra.isNotEmpty()) {
                    CalendarResult.Success(order(extra))
                } else {
                    fromProvider
                }

            extra.isNotEmpty() ->
                CalendarResult.Success(
                    order((fromProvider as CalendarResult.Success).value + extra)
                )

            else -> fromProvider
        }
    }
}
