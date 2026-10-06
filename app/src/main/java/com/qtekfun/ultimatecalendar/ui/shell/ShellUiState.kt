// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import com.qtekfun.ultimatecalendar.domain.navigation.AccountCalendars
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import java.time.DayOfWeek
import java.time.LocalDate

/** Everything the app shell draws: one immutable value, replaced as things change. */
data class ShellUiState(
    val view: CalendarView,
    /** The day the view is anchored on. */
    val date: LocalDate,
    val today: LocalDate,
    /** The days [view] shows around [date]. */
    val range: DateRange,
    val firstDayOfWeek: DayOfWeek,
    val accounts: List<AccountCalendars> = emptyList(),
    /** The calendars could not be read (permission, provider). */
    val calendarsFailed: Boolean = false,
    /** The phone's own accounts cannot be read: CalDAV and subscriptions still work. */
    val calendarPermissionMissing: Boolean = false,
    val pendingInvitations: Int = 0,
    /** Show the week number under the month title (the setting arrives with T23). */
    val showWeekNumbers: Boolean = false
)
