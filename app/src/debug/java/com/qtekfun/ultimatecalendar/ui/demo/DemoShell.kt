// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.navigation.AccountCalendars
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.domain.navigation.ViewPeriods
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.ui.shell.ShellActions
import com.qtekfun.ultimatecalendar.ui.shell.ShellContent
import com.qtekfun.ultimatecalendar.ui.shell.ShellUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

private const val WINDOW_DAYS = 45L

/** The real shell over demo calendars read through [DemoCalendarSource], with no provider. */
@Composable
internal fun DemoShell(options: DemoOptions) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val source = remember { DemoCalendarSource(zone, today) }
    var view by rememberSaveable { mutableStateOf(options.view) }
    val start = today.plusDays(options.offsetDays.toLong()).toEpochDay()
    var epochDay by rememberSaveable { mutableStateOf(start) }
    var hidden by remember { mutableStateOf(emptySet<Long>()) }
    val date = LocalDate.ofEpochDay(epochDay)

    val instances by produceState(emptyList<EventInstance>(), options.empty) {
        val range = TimeRange(
            today.minusDays(WINDOW_DAYS).atStartOfDay(zone).toInstant(),
            today.plusDays(WINDOW_DAYS).atStartOfDay(zone).toInstant()
        )
        val found = source.instances(range)
        value = if (options.empty || found !is CalendarResult.Success) emptyList() else found.value
    }
    val calendars = DemoData.calendars.map { it.copy(visible = it.id.value !in hidden) }
    val visibleIds = calendars.filter { it.visible }.map { it.id }.toSet()
    val colors = DemoData.calendars.associate { it.id to it.color }
    val shown = instances.filter { it.calendarId in visibleIds }

    val state = ShellUiState(
        view = view,
        date = date,
        today = today,
        range = ViewPeriods.range(view, date, DayOfWeek.MONDAY),
        firstDayOfWeek = DayOfWeek.MONDAY,
        accounts = AccountCalendars.group(calendars),
        pendingInvitations = shown.count { it.selfStatus == AttendeeStatus.NEEDS_ACTION },
        showWeekNumbers = options.weeks
    )
    fun move(by: Int) {
        epochDay = ViewPeriods.shift(view, date, by).toEpochDay()
    }
    val actions = ShellActions(
        onSelectView = { view = it },
        onSelectDate = { epochDay = it.toEpochDay() },
        onToday = { epochDay = today.toEpochDay() },
        onPrevious = { move(-1) },
        onNext = { move(1) },
        onSetCalendarVisible = { id: CalendarId, visible ->
            hidden = if (visible) hidden - id.value else hidden + id.value
        }
    )
    ShellContent(state, actions, startWithDrawerOpen = options.drawer) { period, padding ->
        val range = ViewPeriods.range(period.view, period.date, DayOfWeek.MONDAY)
        val days = generateSequence(range.start) { it.plusDays(1) }
            .takeWhile { it < range.endExclusive }
            .toList()
        DemoAgenda(demoDays(days, shown, zone), today, colors, zone, padding)
    }
}
