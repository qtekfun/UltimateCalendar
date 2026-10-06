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
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
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
