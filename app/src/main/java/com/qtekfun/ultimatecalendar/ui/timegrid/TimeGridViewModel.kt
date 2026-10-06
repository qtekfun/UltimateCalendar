// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.timegrid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridLayout
import com.qtekfun.ultimatecalendar.domain.timegrid.TimeGridPage
import com.qtekfun.ultimatecalendar.notify.SystemZone
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** What one page of a day grid shows; [failed] when the events could not be read. */
data class TimeGridState(val page: TimeGridPage, val failed: Boolean = false)

/** The current moment and the device's zone, to draw the "now" line. */
data class GridNow(val instant: Instant, val zone: ZoneId)

/**
 * Feeds the Day and 3 days views (RF-03): the page of any range of days, read from
 * [CalendarRepository.instances] and laid out by [TimeGridLayout], and the clock of the "now"
 * line. The layout runs off the main thread. The pager asks for the pages it shows; nothing is
 * expanded or computed in the UI.
 */
@HiltViewModel
class TimeGridViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val clock: Clock,
    private val zone: SystemZone,
    @IoDispatcher private val layoutDispatcher: CoroutineDispatcher
) : ViewModel() {
    /** The instant and zone now, renewed at every minute change. */
    val now: StateFlow<GridNow> = flow {
        while (true) {
            val instant = clock.instant()
            emit(GridNow(instant, zone.current()))
            delay(MILLIS_PER_MINUTE - instant.toEpochMilli().mod(MILLIS_PER_MINUTE))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), current())

    /** The empty page of [range], to draw while the first read is under way. */
    fun emptyPage(range: DateRange): TimeGridState =
        TimeGridState(TimeGridLayout.build(range, zone.current(), emptyList()))

    /** The page of [range], read again whenever the calendars or their settings change. */
    fun page(range: DateRange): Flow<TimeGridState> = combine(
        repository.instances(range.toTimeRange(zone.current())),
        repository.calendars()
    ) { instances, calendars ->
        val colors = calendars.getOrNull().orEmpty().associate { it.id to it.color }
        when (instances) {
            is CalendarResult.Failure -> emptyPage(range).copy(failed = true)

            is CalendarResult.Success -> TimeGridState(
                TimeGridLayout.build(range, zone.current(), instances.value, colors)
            )
        }
    }.flowOn(layoutDispatcher)

    private fun current() = GridNow(clock.instant(), zone.current())

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
