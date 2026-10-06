// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import java.time.LocalDate

/** The days a drag can reach: [dayCount] days from [first] (the days of the page on screen). */
data class DayBounds(val first: LocalDate, val dayCount: Int) {
    init {
        require(dayCount >= 1) { "A page has at least one day" }
    }

    val last: LocalDate get() = first.plusDays(dayCount - 1L)

    companion object {
        fun of(page: TimeGridPage) = DayBounds(page.days.first(), page.days.size)
    }
}
