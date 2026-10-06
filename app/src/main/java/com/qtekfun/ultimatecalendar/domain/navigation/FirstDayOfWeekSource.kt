// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

import java.time.DayOfWeek

/** The day weeks start on: the locale's for now, a setting later (T23). */
fun interface FirstDayOfWeekSource {
    fun current(): DayOfWeek
}
