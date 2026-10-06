// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The pages of a swipeable view of whole days (Day, 3 days, Week): page [CENTER] starts on
 * [anchor] and every page away is one more period, as [ViewPeriods.shift] moves them. A pager
 * built on it is told a date from outside (Today, the date picker) with [pageOf], and tells the
 * date of the page it settled on with [dateAt]. A date that does not fall on a page boundary
 * has no page: the owner then makes a new [PeriodPages] anchored on it.
 */
class PeriodPages(private val view: CalendarView, val anchor: LocalDate) {
    private val stepDays: Long =
        ChronoUnit.DAYS.between(anchor, ViewPeriods.shift(view, anchor, 1))

    init {
        require(view in FIXED_PERIODS) { "Only views of a fixed number of days page this way" }
    }

    /** The start date of [page]. */
    fun dateAt(page: Int): LocalDate = ViewPeriods.shift(view, anchor, page - CENTER)

    /** The page that starts on [date], or null when no page does. */
    fun pageOf(date: LocalDate): Int? {
        val days = ChronoUnit.DAYS.between(anchor, date)
        if (days % stepDays != 0L) return null
        val page = CENTER + days / stepDays
        return if (page in 0 until COUNT) page.toInt() else null
    }

    companion object {
        const val CENTER = 10_000
        const val COUNT = 2 * CENTER
        private val FIXED_PERIODS = setOf(
            CalendarView.DAY,
            CalendarView.THREE_DAYS,
            CalendarView.WEEK
        )
    }
}
