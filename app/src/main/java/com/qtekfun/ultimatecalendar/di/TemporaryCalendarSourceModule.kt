// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * TEMPORARY: binds [CalendarSource] to a source with no calendars so the Hilt graph (and the
 * app shell, T13) works before `ProviderCalendarSource` exists. T05 must delete this file and
 * bind the real source; keeping both fails the build with a duplicate binding on purpose.
 */
@Module
@InstallIn(SingletonComponent::class)
object TemporaryCalendarSourceModule {
    @Provides
    @Singleton
    fun calendarSource(): CalendarSource = NoCalendarSource
}

@Suppress("TooManyFunctions")
private object NoCalendarSource : CalendarSource {
    private val unavailable = CalendarResult.Failure(CalendarError.SourceFailure("no source yet"))

    override val changes: Flow<Unit> = emptyFlow()

    override suspend fun calendars() = CalendarResult.Success(emptyList<CalendarInfo>())

    override suspend fun instances(range: TimeRange, calendarIds: Set<CalendarId>?) =
        CalendarResult.Success(emptyList<EventInstance>())

    override suspend fun event(id: EventId): CalendarResult<Event> = unavailable

    override suspend fun create(draft: EventDraft): CalendarResult<EventId> = unavailable

    override suspend fun update(event: Event): CalendarResult<Unit> = unavailable

    override suspend fun delete(id: EventId): CalendarResult<Unit> = unavailable

    override suspend fun editInstance(
        id: EventId,
        originalStart: Instant,
        changes: EventDraft
    ): CalendarResult<Unit> = unavailable

    override suspend fun cancelInstance(id: EventId, originalStart: Instant): CalendarResult<Unit> =
        unavailable

    override suspend fun respond(id: EventId, status: AttendeeStatus): CalendarResult<Unit> =
        unavailable
}
