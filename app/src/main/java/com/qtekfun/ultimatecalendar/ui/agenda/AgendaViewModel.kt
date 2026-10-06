// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaDays
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaItem
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaItems
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaWindow
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import com.qtekfun.ultimatecalendar.domain.navigation.ViewPeriods
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.SystemZone
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update

/** Where the agenda is in reading its events. */
enum class AgendaStatus {
    /** Nothing to show yet: the first read, or looking further on for the first event. */
    LOADING,

    READY,

    /** The events could not be read (permission, provider). */
    FAILED
}

/**
 * What the agenda draws. [generation] grows each time it is asked to show another date, so the
 * list knows when to scroll to [anchor]; [range] is the window of days read so far.
 */
data class AgendaState(
    val anchor: LocalDate,
    val generation: Int,
    val range: DateRange,
    val zone: ZoneId,
    val items: List<AgendaItem> = emptyList(),
    val status: AgendaStatus = AgendaStatus.LOADING
)

/**
 * Feeds the Agenda view (RF-03): a window of days around the date it was asked to show, read from
 * [CalendarRepository.instances] and bucketed by [AgendaDays]. The list asks for [earlier] and
 * [later] chunks as it nears its edges; [show] starts over on another date. Nothing is expanded
 * or grouped in the UI.
 */
@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val clock: Clock,
    private val zone: SystemZone,
    @IoDispatcher private val layoutDispatcher: CoroutineDispatcher
) : ViewModel() {
    private data class Request(val generation: Int, val window: AgendaWindow)

    private val request = MutableStateFlow(Request(0, AgendaWindow.around(today())))
    private var shownGeneration = -1

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AgendaState> = request.transformLatest { current ->
        if (current.generation != shownGeneration) {
            shownGeneration = current.generation
            emit(loading(current))
        }
        load(current).collect { loaded ->
            if (loaded.status == AgendaStatus.READY &&
                loaded.items.isEmpty() &&
                current.window.canExtendLater
            ) {
                // Nothing yet: keep looking ahead instead of claiming there is nothing planned.
                extend(current.generation) { it.later() }
                emit(loading(current))
            } else {
                emit(loaded)
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        loading(request.value)
    )

    /** Starts over around [date]: the list shows a skeleton, then scrolls to it. */
    fun show(date: LocalDate) {
        request.update { Request(it.generation + 1, AgendaWindow.around(date)) }
    }

    /** Loads a chunk of earlier days (when the list nears its top). */
    fun earlier() = extend(request.value.generation) { it.earlier() }

    /** Loads a chunk of later days (when the list nears its bottom). */
    fun later() = extend(request.value.generation) { it.later() }

    private fun extend(generation: Int, change: (AgendaWindow) -> AgendaWindow) {
        request.update {
            if (it.generation == generation) it.copy(window = change(it.window)) else it
        }
    }

    private fun load(current: Request): Flow<AgendaState> {
        val window = current.window
        val device = zone.current()
        return combine(
            repository.instances(window.range.toTimeRange(device)),
            repository.calendars()
        ) { instances, calendars ->
            val colors = calendars.getOrNull().orEmpty().associate { it.id to it.color }
            when (instances) {
                is CalendarResult.Failure -> loading(current).copy(status = AgendaStatus.FAILED)

                is CalendarResult.Success -> loading(current).copy(
                    items = AgendaItems.flatten(
                        AgendaDays.build(window.range, device, instances.value, colors)
                    ),
                    status = AgendaStatus.READY
                )
            }
        }.flowOn(layoutDispatcher)
    }

    private fun loading(current: Request) = AgendaState(
        anchor = current.window.anchor,
        generation = current.generation,
        range = current.window.range,
        zone = zone.current()
    )

    private fun today(): LocalDate = ViewPeriods.today(clock, zone.current())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
