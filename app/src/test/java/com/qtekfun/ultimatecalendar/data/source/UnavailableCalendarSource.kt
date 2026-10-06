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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** A source whose every call fails with [CalendarError.SourceFailure]; nothing ever changes. */
@Suppress("TooManyFunctions")
object UnavailableCalendarSource : CalendarSource {
    override val changes: Flow<Unit> = emptyFlow()

    override suspend fun calendars(): CalendarResult<List<CalendarInfo>> = unavailable()

    override suspend fun instances(
        range: TimeRange,
        calendarIds: Set<CalendarId>?
    ): CalendarResult<List<EventInstance>> = unavailable()

    override suspend fun instancesWithReminders(
        range: TimeRange,
        calendarIds: Set<CalendarId>?
    ): CalendarResult<List<EventReminders>> = unavailable()

    override suspend fun search(
        query: String,
        calendarIds: Set<CalendarId>?,
        range: TimeRange?
    ): CalendarResult<List<SearchableEvent>> = unavailable()

    override suspend fun event(id: EventId): CalendarResult<Event> = unavailable()

    override suspend fun create(draft: EventDraft): CalendarResult<EventId> = unavailable()

    override suspend fun update(event: Event): CalendarResult<Unit> = unavailable()

    override suspend fun delete(id: EventId): CalendarResult<Unit> = unavailable()

    override suspend fun editInstance(
        id: EventId,
        originalStart: Instant,
        changes: EventDraft
    ): CalendarResult<Unit> = unavailable()

    override suspend fun cancelInstance(id: EventId, originalStart: Instant) = unavailable()

    override suspend fun respond(id: EventId, status: AttendeeStatus) = unavailable()
}

/** The one failure every call of [UnavailableCalendarSource] reports. */
private fun unavailable(): CalendarResult.Failure =
    CalendarResult.Failure(CalendarError.SourceFailure("no calendar source is bound"))
