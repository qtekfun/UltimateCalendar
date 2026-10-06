// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.month

import androidx.lifecycle.ViewModel
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.month.MonthGrid
import com.qtekfun.ultimatecalendar.domain.month.MonthLayout
import com.qtekfun.ultimatecalendar.domain.month.MonthPage
import com.qtekfun.ultimatecalendar.domain.month.MonthQuickCreate
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.SystemZone
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** What one page of the Month view shows; [failed] when the events could not be read. */
data class MonthState(val page: MonthPage, val failed: Boolean = false)

/**
 * Feeds the Month view (RF-03): the page of any month, read from
 * [CalendarRepository.instances] once for the whole visible grid and laid out in lanes by
 * [MonthLayout] off the main thread. The pager asks for the pages it shows; nothing is expanded
 * or computed in the UI.
 */
@HiltViewModel
class MonthViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val clock: Clock,
    private val zone: SystemZone,
    @IoDispatcher private val layoutDispatcher: CoroutineDispatcher
) : ViewModel() {
    /** The empty page of [month], to draw while the first read is under way. */
    fun emptyPage(month: YearMonth, firstDayOfWeek: DayOfWeek): MonthState = MonthState(
        MonthLayout.build(MonthGrid.of(month, firstDayOfWeek), zone.current(), emptyList())
    )

    /** The page of [month], read again whenever the calendars or their settings change. */
    fun page(month: YearMonth, firstDayOfWeek: DayOfWeek): Flow<MonthState> {
        val grid = MonthGrid.of(month, firstDayOfWeek)
        return combine(
            repository.instances(grid.range.toTimeRange(zone.current())),
            repository.calendars()
        ) { instances, calendars ->
            when (instances) {
                is CalendarResult.Failure ->
                    MonthState(MonthLayout.build(grid, zone.current(), emptyList()), failed = true)

                is CalendarResult.Success -> {
                    val colors = calendars.getOrNull().orEmpty().associate { it.id to it.color }
                    MonthState(MonthLayout.build(grid, zone.current(), instances.value, colors))
                }
            }
        }.flowOn(layoutDispatcher)
    }

    /** The zone the page was laid out in, to show times in it. */
    fun zone(): ZoneId = zone.current()

    /** Where a long press on [date] starts a new event. */
    fun quickCreateAt(date: LocalDate): LocalDateTime =
        MonthQuickCreate.startFor(date, LocalDateTime.now(clock.withZone(zone.current())))
}
