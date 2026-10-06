// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.month

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * The pages of the swipeable Month view: page [CENTER] is the month of [anchor] and every page
 * away is one more month. The pager is told a date from outside (Today, the date picker) with
 * [pageOf] and reports the date of the page it settles on with [dateAt].
 */
class MonthPages(val anchor: LocalDate) {
    private val anchorMonth = YearMonth.from(anchor)

    /** The month shown on [page]. */
    fun monthAt(page: Int): YearMonth = anchorMonth.plusMonths((page - CENTER).toLong())

    /**
     * The date to select when the pager settles on [page] while [selected] was selected: the
     * same day of the month, clamped to the shorter month (31 Jan -> 28 or 29 Feb).
     */
    fun dateAt(page: Int, selected: LocalDate): LocalDate {
        val month = monthAt(page)
        return month.atDay(minOf(selected.dayOfMonth, month.lengthOfMonth()))
    }

    /** The page that shows the month of [date], or null when it is out of reach. */
    fun pageOf(date: LocalDate): Int? {
        val page = CENTER + ChronoUnit.MONTHS.between(anchorMonth, YearMonth.from(date))
        return if (page in 0 until COUNT) page.toInt() else null
    }

    companion object {
        const val CENTER = 1_200
        const val COUNT = 2 * CENTER
    }
}
