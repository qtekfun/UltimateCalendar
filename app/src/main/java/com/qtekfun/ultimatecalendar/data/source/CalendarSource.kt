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
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Where calendars and events live: the Android calendar provider (`ProviderCalendarSource`) and,
 * in phase 6, CalDAV. The UI and `domain` only see this interface. Every function is main-safe
 * and reports failures as a [CalendarResult], never as an exception. The shared
 * `CalendarSourceContract` defines how every implementation must behave.
 *
 * Splitting a series ("this and following") is domain logic built from [update],
 * [editInstance], [cancelInstance] and [create]; a source only offers those primitives.
 */
@Suppress("TooManyFunctions")
interface CalendarSource {
    /** Emits whenever calendars, events or attendees may have changed (provider observer). */
    val changes: Flow<Unit>

    /** All calendars, including hidden ones: invitations are looked for in every calendar. */
    suspend fun calendars(): CalendarResult<List<CalendarInfo>>

    /**
     * The occurrences that overlap [range], repetitions already expanded by the source, in
     * [calendarIds] or in all calendars when null. All-day instances are dated in UTC.
     */
    suspend fun instances(
        range: TimeRange,
        calendarIds: Set<CalendarId>? = null
    ): CalendarResult<List<EventInstance>>

    /**
     * The same occurrences as [instances], each with what a reminder needs, read in bulk (RF-08):
     * the reminders of its event (every method; [EventReminders.usesDefaults] when it asks for
     * the calendar's defaults, which are not in the list), its description and its instance.
     * For a changed occurrence the reminders are its own. One query per table whatever the
     * number of events, so it can be asked for a month of the whole phone at every change.
     */
    suspend fun instancesWithReminders(
        range: TimeRange,
        calendarIds: Set<CalendarId>? = null
    ): CalendarResult<List<EventReminders>>

    /**
     * The events that match [query] (RF-09), each series once: every word of the query (see
     * `SearchQuery`) is found, without case or accents, in the title, location, description or an
     * attendee's name or address, as `SearchMatcher` decides. Limited to [calendarIds] (all
     * calendars when null, none when empty) and, when [range] is given, to events with an
     * occurrence in it. Single changed occurrences are not searched on their own; the order of
     * the result is not defined (ranking is domain logic).
     */
    suspend fun search(
        query: String,
        calendarIds: Set<CalendarId>? = null,
        range: TimeRange? = null
    ): CalendarResult<List<SearchableEvent>>

    /** The event with its attendees and reminders; for a series, the series itself. */
    suspend fun event(id: EventId): CalendarResult<Event>

    suspend fun create(draft: EventDraft): CalendarResult<EventId>

    /** Replaces the stored event (a whole series, if it repeats) with [event]. */
    suspend fun update(event: Event): CalendarResult<Unit>

    /** Deletes the event, or the whole series. */
    suspend fun delete(id: EventId): CalendarResult<Unit>

    /**
     * Changes one occurrence of a series, the one that originally started at [originalStart],
     * using the fields of [changes]; the rest of the series stays as it is.
     */
    suspend fun editInstance(
        id: EventId,
        originalStart: Instant,
        changes: EventDraft
    ): CalendarResult<Unit>

    /** Removes one occurrence of a series; the rest of the series stays as it is. */
    suspend fun cancelInstance(id: EventId, originalStart: Instant): CalendarResult<Unit>

    /**
     * Answers an invitation as the owner of the event's calendar. The source tells the
     * organizer. Fails with `Invalid` when the user is not an attendee.
     */
    suspend fun respond(id: EventId, status: AttendeeStatus): CalendarResult<Unit>
}
