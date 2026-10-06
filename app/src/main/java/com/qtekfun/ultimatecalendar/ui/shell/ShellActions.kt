// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import java.time.LocalDate

/** What the shell can ask for; the screen is stateless and calls these. */
data class ShellActions(
    val onSelectView: (CalendarView) -> Unit = {},
    val onSelectDate: (LocalDate) -> Unit = {},
    val onToday: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSetCalendarVisible: (CalendarId, Boolean) -> Unit = { _, _ -> },
    val onSearch: () -> Unit = {},
    val onInvitations: () -> Unit = {},
    val onNewEvent: () -> Unit = {},
    val onSettings: () -> Unit = {}
)
