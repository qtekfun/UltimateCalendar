// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.month

import java.time.DayOfWeek
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/** Where the pages of the month get their events: the ViewModel, or fixed data in previews. */
internal interface MonthPageSource {
    fun initial(month: YearMonth, firstDayOfWeek: DayOfWeek): MonthState

    fun page(month: YearMonth, firstDayOfWeek: DayOfWeek): Flow<MonthState>
}
