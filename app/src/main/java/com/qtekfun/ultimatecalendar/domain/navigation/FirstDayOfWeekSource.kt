// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The day weeks start on: the one picked in Settings, else the locale's (RF-10). */
fun interface FirstDayOfWeekSource {
    fun current(): DayOfWeek

    /** The day now and every time it changes, so an open view follows the setting. */
    fun changes(): Flow<DayOfWeek> = flowOf(current())
}
