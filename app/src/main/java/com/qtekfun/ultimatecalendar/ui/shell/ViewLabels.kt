// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.annotation.StringRes
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView

/** The name of a view, in the drawer and in the placeholders. */
@StringRes
fun CalendarView.label(): Int = when (this) {
    CalendarView.AGENDA -> R.string.shell_view_agenda
    CalendarView.DAY -> R.string.shell_view_day
    CalendarView.THREE_DAYS -> R.string.shell_view_three_days
    CalendarView.WEEK -> R.string.shell_view_week
    CalendarView.MONTH -> R.string.shell_view_month
}
