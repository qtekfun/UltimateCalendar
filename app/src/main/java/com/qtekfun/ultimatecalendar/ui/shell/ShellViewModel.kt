// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.navigation.AccountCalendars
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.domain.navigation.FirstDayOfWeekSource
import com.qtekfun.ultimatecalendar.domain.navigation.InitialViewSource
import com.qtekfun.ultimatecalendar.domain.navigation.PendingInvitations
import com.qtekfun.ultimatecalendar.domain.navigation.ViewPeriods
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.SystemZone
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the app shell (RF-02, RF-03): which view and date are selected, the calendars of the
 * drawer and the invitation count. The screen reads [state] and calls the functions below; the
 * date arithmetic lives in [ViewPeriods]. The selection survives process death.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val repository: CalendarRepository,
    invitations: PendingInvitations,
    private val clock: Clock,
    private val zone: SystemZone,
    private val firstDayOfWeek: FirstDayOfWeekSource,
    private val initialView: InitialViewSource
) : ViewModel() {
    private data class Selection(val view: CalendarView, val date: LocalDate)

    private val selection = MutableStateFlow(restoreSelection())

    val state: StateFlow<ShellUiState> = combine(
        selection,
        repository.calendars(),
        invitations.count(),
        firstDayOfWeek.changes()
    ) { selected, calendars, pending, first ->
        build(
            selected,
            first,
            accounts = calendars.getOrNull()?.let(AccountCalendars::group).orEmpty(),
            failed = calendars is CalendarResult.Failure,
            pending = pending
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        build(
            selection.value,
            firstDayOfWeek.current(),
            accounts = emptyList(),
            failed = false,
            pending = 0
        )
    )

    fun selectView(view: CalendarView) = select { it.copy(view = view) }

    fun selectDate(date: LocalDate) = select { it.copy(date = date) }

    fun goToToday() = select { it.copy(date = today()) }

    fun previous() = select { it.copy(date = ViewPeriods.shift(it.view, it.date, -1)) }

    fun next() = select { it.copy(date = ViewPeriods.shift(it.view, it.date, 1)) }

    /** Shows or hides a calendar here only: the source is never written (RF-02). */
    fun setCalendarVisible(id: CalendarId, visible: Boolean) {
        viewModelScope.launch {
            repository.saveSettings(id, repository.settings(id).copy(visible = visible))
        }
    }

    /**
     * Gives a calendar the name and color the user typed and picked, on this phone only (RF-02).
     * [pickedColor] null goes back to the source's color; a blank [typedName], to its name.
     */
    fun saveCalendarLook(calendar: CalendarInfo, typedName: String, pickedColor: Int?) {
        viewModelScope.launch {
            val current = repository.settings(calendar.id)
            repository.saveSettings(
                calendar.id,
                current.withLook(calendar.displayName, typedName, calendar.color, pickedColor)
            )
        }
    }

    private fun build(
        selected: Selection,
        first: DayOfWeek,
        accounts: List<AccountCalendars>,
        failed: Boolean,
        pending: Int
    ): ShellUiState = ShellUiState(
        view = selected.view,
        date = selected.date,
        today = today(),
        range = ViewPeriods.range(selected.view, selected.date, first),
        firstDayOfWeek = first,
        accounts = accounts,
        calendarsFailed = failed,
        pendingInvitations = pending
    )

    private fun today(): LocalDate = ViewPeriods.today(clock, zone.current())

    private fun select(change: (Selection) -> Selection) {
        selection.update(change)
        saved[KEY_VIEW] = selection.value.view.name
        saved[KEY_DATE] = selection.value.date.toEpochDay()
    }

    private fun restoreSelection(): Selection {
        val view = saved.get<String>(KEY_VIEW)
            ?.let { name -> CalendarView.entries.firstOrNull { it.name == name } }
        val date = saved.get<Long>(KEY_DATE)?.let(LocalDate::ofEpochDay)
        return Selection(view ?: initialView.initial(), date ?: today())
    }

    private companion object {
        const val KEY_VIEW = "view"
        const val KEY_DATE = "date"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
