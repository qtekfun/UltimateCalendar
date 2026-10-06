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
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceRules
import com.qtekfun.ultimatecalendar.domain.reminders.EventReminders
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.search.SearchMatcher
import com.qtekfun.ultimatecalendar.domain.search.SearchQuery
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * An in-memory [CalendarSource] for unit tests. It must behave like the real provider as the
 * `CalendarSourceContract` defines it; when they differ, the fake is wrong. It expands daily and
 * weekly repetitions that end (COUNT or UNTIL) and rejects other rules.
 */
class FakeCalendarSource(calendars: List<CalendarInfo> = emptyList()) : CalendarSource {
    private val calendarsById = calendars.associateBy { it.id }.toMutableMap()
    private val events = linkedMapOf<EventId, Event>()
    private val exceptions = mutableMapOf<EventId, MutableMap<Instant, Exception>>()
    private val withDefaults = mutableSetOf<EventId>()
    private var lastId = 0L
    private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = CHANGES_BUFFER)

    override val changes: Flow<Unit> get() = changed

    /**
     * Makes [id] ask for "the calendar's default reminders", as an event of the provider does with
     * `MINUTES_DEFAULT`, which no write of the API can produce.
     */
    fun useDefaultReminders(id: EventId) {
        withDefaults += id
        changed.tryEmit(Unit)
    }

    fun addCalendar(calendar: CalendarInfo) {
        calendarsById[calendar.id] = calendar
        changed.tryEmit(Unit)
    }

    override suspend fun calendars() = CalendarResult.Success(calendarsById.values.toList())

    override suspend fun instances(
        range: TimeRange,
        calendarIds: Set<CalendarId>?
    ): CalendarResult<List<EventInstance>> = CalendarResult.Success(
        occurrences(range, calendarIds).map {
            it.instance
        }
    )

    override suspend fun instancesWithReminders(
        range: TimeRange,
        calendarIds: Set<CalendarId>?
    ): CalendarResult<List<EventReminders>> =
        CalendarResult.Success(occurrences(range, calendarIds))

    private fun occurrences(range: TimeRange, calendarIds: Set<CalendarId>?) = events.values
        .filter { calendarIds == null || it.calendarId in calendarIds }
        .flatMap { instancesOf(it, range) }
        .sortedBy { it.instance.time.startIn(ZoneOffset.UTC) }

    override suspend fun search(
        query: String,
        calendarIds: Set<CalendarId>?,
        range: TimeRange?
    ): CalendarResult<List<SearchableEvent>> {
        val words = SearchQuery.of(query)
        val found = events.values
            .filter { calendarIds == null || it.calendarId in calendarIds }
            .filter { range == null || instancesOf(it, range).isNotEmpty() }
            .map { SearchableEvent.of(it) }
            .filter { SearchMatcher.match(words, it) != null }
        return CalendarResult.Success(found)
    }

    override suspend fun event(id: EventId): CalendarResult<Event> =
        events[id]?.let { CalendarResult.Success(it) } ?: failure(CalendarError.NotFound)

    override suspend fun create(draft: EventDraft): CalendarResult<EventId> {
        val calendar = calendarsById[draft.calendarId]
        val error = when {
            calendar == null -> CalendarError.NotFound
            !calendar.access.canCreate -> CalendarError.ReadOnly
            else -> invalidRule(draft.rrule)
        }
        if (error != null) return failure(error)
        val id = EventId(++lastId)
        events[id] = draft.toEvent(id, organizer = requireNotNull(calendar).ownerEmail)
        changed.tryEmit(Unit)
        return CalendarResult.Success(id)
    }

    override suspend fun update(event: Event): CalendarResult<Unit> = editable(event.id) { stored ->
        val error = invalidRule(event.rrule)
        if (error == null) events[event.id] = event.copy(calendarId = stored.calendarId)
        error?.let { failure(it) } ?: CalendarResult.Success(Unit)
    }

    override suspend fun delete(id: EventId): CalendarResult<Unit> = editable(id) {
        events.remove(id)
        exceptions.remove(id)
        CalendarResult.Success(Unit)
    }

    override suspend fun editInstance(
        id: EventId,
        originalStart: Instant,
        changes: EventDraft
    ): CalendarResult<Unit> = markInstance(id, originalStart, Exception.Edited(changes))

    override suspend fun cancelInstance(id: EventId, originalStart: Instant) =
        markInstance(id, originalStart, Exception.Cancelled)

    override suspend fun respond(id: EventId, status: AttendeeStatus): CalendarResult<Unit> {
        val event = events[id]
        val calendar = event?.let { calendarsById.getValue(it.calendarId) }
        val me = listOfNotNull(calendar?.ownerEmail)
        val error = when {
            event == null || calendar == null -> CalendarError.NotFound
            !calendar.access.canRespond -> CalendarError.ReadOnly
            event.attendees.none { it.isOneOf(me) } -> CalendarError.Invalid("not an attendee")
            else -> null
        }
        if (error != null) return failure(error)
        val answered = requireNotNull(event)
        events[id] = answered.copy(
            attendees = answered.attendees.map {
                if (it.isOneOf(me)) it.copy(status = status) else it
            }
        )
        changed.tryEmit(Unit)
        return CalendarResult.Success(Unit)
    }

    private fun markInstance(
        id: EventId,
        originalStart: Instant,
        exception: Exception
    ): CalendarResult<Unit> = editable(id) { stored ->
        if (FakeOccurrences.of(stored, null).any { it.originalStart == originalStart }) {
            exceptions.getOrPut(id) { mutableMapOf() }[originalStart] = exception
            CalendarResult.Success(Unit)
        } else {
            failure(CalendarError.NotFound)
        }
    }

    private fun editable(
        id: EventId,
        change: (Event) -> CalendarResult<Unit>
    ): CalendarResult<Unit> {
        val event = events[id]
        val result = when {
            event == null -> failure(CalendarError.NotFound)

            !calendarsById.getValue(event.calendarId).access.canEdit ->
                failure(CalendarError.ReadOnly)

            else -> change(event)
        }
        if (result is CalendarResult.Success) changed.tryEmit(Unit)
        return result
    }

    private fun failure(error: CalendarError) = CalendarResult.Failure(error)

    private fun invalidRule(rrule: String?): CalendarError? {
        val rule = rrule?.let { RecurrenceRules.parse(it) }
        val usable = rule != null && rule.frequency in FakeOccurrences.supported &&
            (rule.count != null || rule.until != null)
        return if (rrule == null || usable) null else CalendarError.Invalid("rule")
    }

    private fun instancesOf(event: Event, range: TimeRange): List<EventReminders> {
        val me = listOfNotNull(calendarsById.getValue(event.calendarId).ownerEmail)
        val self = event.attendees.firstOrNull { it.isOneOf(me) }?.status
        return FakeOccurrences.of(event, range.end).mapNotNull { occurrence ->
            val exception = exceptions[event.id]?.get(occurrence.originalStart)
            val edit = (exception as? Exception.Edited)?.draft
            if (exception == Exception.Cancelled) {
                null
            } else {
                EventReminders(
                    instance = EventInstance(
                        eventId = event.id,
                        calendarId = event.calendarId,
                        title = edit?.title ?: event.title,
                        time = edit?.time ?: occurrence.time,
                        location = edit?.location ?: event.location,
                        color = edit?.color ?: event.color,
                        isRecurring = event.isRecurring,
                        selfStatus = self,
                        hasAttendees = event.attendees.isNotEmpty()
                    ),
                    // A changed occurrence is an event of its own in the provider: it has the
                    // reminders and the notes it was changed with.
                    reminders = edit?.reminders ?: event.reminders,
                    description = (edit?.description ?: event.description)?.ifEmpty { null },
                    usesDefaults = event.id in withDefaults
                )
            }
        }.filter { overlaps(it.instance.time, range) }
    }

    private fun overlaps(time: EventTime, range: TimeRange): Boolean {
        val start = time.startIn(ZoneOffset.UTC)
        val end = time.endIn(ZoneOffset.UTC)
        return if (end == start) {
            !start.isBefore(range.start) && start.isBefore(range.end)
        } else {
            start.isBefore(range.end) && end.isAfter(range.start)
        }
    }

    private sealed interface Exception {
        data object Cancelled : Exception

        data class Edited(val draft: EventDraft) : Exception
    }

    private companion object {
        const val CHANGES_BUFFER = 64
    }
}
