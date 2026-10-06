// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.calendar

import com.qtekfun.ultimatecalendar.data.local.dao.CalendarSettingsDao
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.ProviderAccess
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.CalendarSettings
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * What the views read (RF-02, RF-03): calendars and instances from the [CalendarSource] with
 * the local settings of each calendar applied. Every flow reads again when the source reports
 * a change or a local setting changes, so collectors always see current data.
 */
@Singleton
class CalendarRepository @Inject constructor(
    private val source: CalendarSource,
    private val dao: CalendarSettingsDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    private val refreshes = MutableStateFlow(0)

    /**
     * Whether the phone's own calendars cannot be read for want of the calendar permission. A
     * source that cannot tell (a test double) is never denied.
     */
    fun providerDenied(): Flow<Boolean> = (source as? ProviderAccess)?.denied ?: flowOf(false)

    /** Reads everything again, e.g. after the user grants the calendar permission. */
    fun refresh() = refreshes.update { it + 1 }

    /** Every calendar, hidden ones included, with name, color and visibility overrides applied. */
    fun calendars(): Flow<CalendarResult<List<CalendarInfo>>> =
        combine(sourceChanges(), dao.observeAll(), refreshes) { _, stored, _ ->
            val overrides = stored.associate { CalendarId(it.calendarId) to it.toSettings() }
            source.calendars().map { list ->
                list.map { calendar -> overrides[calendar.id]?.applyTo(calendar) ?: calendar }
            }
        }.flowOn(io)

    /** The calendars the user has switched on. */
    fun visibleCalendars(): Flow<CalendarResult<List<CalendarInfo>>> =
        calendars().map { result -> result.map { list -> list.filter { it.visible } } }

    /**
     * The occurrences overlapping [range] in the visible calendars, in the source's order.
     * Repetitions are expanded by the source, never here. It reads again on every calendar or
     * source change, but only emits when the result differs from the last one.
     */
    fun instances(range: TimeRange): Flow<CalendarResult<List<EventInstance>>> =
        visibleCalendars().map { result ->
            when (result) {
                is CalendarResult.Failure -> result

                // An empty filter would mean "every calendar" to the source, not "none".
                is CalendarResult.Success -> if (result.value.isEmpty()) {
                    CalendarResult.Success(emptyList())
                } else {
                    source.instances(range, result.value.map { it.id }.toSet())
                }
            }
        }.distinctUntilChanged().flowOn(io)

    /**
     * The calendar for new events when the user has not chosen one, or the chosen one is gone:
     * the first visible calendar that accepts events, else the first that does. Fails with
     * [CalendarError.NotFound] when no calendar accepts events. The user's own choice lives in
     * `AppSettings.defaultCalendar` and is applied by `EditorCalendars.initial`.
     */
    fun automaticDefaultCalendar(): Flow<CalendarResult<CalendarInfo>> =
        calendars().map { calendars ->
            calendars.flatMap { all ->
                val writable = all.filter { it.access.canCreate }
                val found = writable.firstOrNull { it.visible } ?: writable.firstOrNull()
                found?.let { CalendarResult.Success(it) }
                    ?: CalendarResult.Failure(CalendarError.NotFound)
            }
        }

    /** Stores [settings] for a calendar; an empty one forgets the overrides. */
    suspend fun saveSettings(id: CalendarId, settings: CalendarSettings) = withContext(io) {
        if (settings.isEmpty) {
            dao.clear(id.value)
        } else {
            dao.save(
                CalendarSettingsEntity(
                    id.value,
                    settings.displayName,
                    settings.color,
                    settings.visible
                )
            )
        }
    }

    /** The local overrides of a calendar, empty when there are none. */
    suspend fun settings(id: CalendarId): CalendarSettings = withContext(io) {
        dao.find(id.value)?.toSettings() ?: CalendarSettings()
    }

    private fun sourceChanges(): Flow<Unit> = source.changes.onStart { emit(Unit) }

    private fun CalendarSettingsEntity.toSettings() = CalendarSettings(displayName, color, visible)
}
