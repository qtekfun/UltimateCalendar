// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.calendar

import com.qtekfun.ultimatecalendar.data.local.dao.CalendarSettingsDao
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DefaultCalendarEntity
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
    /** Every calendar, hidden ones included, with name, color and visibility overrides applied. */
    fun calendars(): Flow<CalendarResult<List<CalendarInfo>>> =
        combine(sourceChanges(), dao.observeAll()) { _, stored ->
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
     * Repetitions are expanded by the source, never here.
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
        }.flowOn(io)

    /**
     * The calendar for new events: the one the user chose if it still exists and accepts
     * events, else the first visible calendar that does, else the first that does. Fails with
     * [CalendarError.NotFound] when no calendar accepts events.
     */
    fun defaultCalendar(): Flow<CalendarResult<CalendarInfo>> =
        combine(calendars(), dao.observeDefaultCalendar()) { calendars, chosen ->
            calendars.flatMap { all ->
                val writable = all.filter { it.access.canCreate }
                val found = writable.firstOrNull { it.id.value == chosen }
                    ?: writable.firstOrNull { it.visible }
                    ?: writable.firstOrNull()
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

    /** Chooses the calendar for new events; null goes back to the automatic choice. */
    suspend fun setDefaultCalendar(id: CalendarId?) = withContext(io) {
        if (id == null) {
            dao.clearDefaultCalendar()
        } else {
            dao.saveDefaultCalendar(DefaultCalendarEntity(calendarId = id.value))
        }
    }

    private fun sourceChanges(): Flow<Unit> = source.changes.onStart { emit(Unit) }

    private fun CalendarSettingsEntity.toSettings() = CalendarSettings(displayName, color, visible)
}
