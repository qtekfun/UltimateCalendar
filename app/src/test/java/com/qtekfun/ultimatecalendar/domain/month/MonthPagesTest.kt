// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.month

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MonthPagesTest {
    private val pages = MonthPages(LocalDate.parse("2026-10-15"))

    @Test
    fun `the center page is the month of the anchor and the others step by month`() {
        assertEquals(YearMonth.parse("2026-10"), pages.monthAt(MonthPages.CENTER))
        assertEquals(YearMonth.parse("2026-11"), pages.monthAt(MonthPages.CENTER + 1))
        assertEquals(YearMonth.parse("2027-01"), pages.monthAt(MonthPages.CENTER + 3))
        assertEquals(YearMonth.parse("2026-09"), pages.monthAt(MonthPages.CENTER - 1))
    }

    @Test
    fun `any day of a month finds its page`() {
        assertEquals(MonthPages.CENTER, pages.pageOf(LocalDate.parse("2026-10-01")))
        assertEquals(MonthPages.CENTER, pages.pageOf(LocalDate.parse("2026-10-31")))
        assertEquals(MonthPages.CENTER + 2, pages.pageOf(LocalDate.parse("2026-12-24")))
        assertEquals(MonthPages.CENTER - 10, pages.pageOf(LocalDate.parse("2025-12-31")))
    }

    @Test
    fun `a month out of reach has no page`() {
        val years = MonthPages.CENTER / MONTHS_PER_YEAR
        assertNull(pages.pageOf(LocalDate.of(2026 + years + 1, 1, 1)))
        assertNull(pages.pageOf(LocalDate.of(2026 - years - 1, 1, 1)))
    }

    @Test
    fun `settling on a month keeps the day of the month and clamps it to a short month`() {
        val end = MonthPages(LocalDate.parse("2027-01-31"))
        val selected = LocalDate.parse("2027-01-31")

        assertEquals(LocalDate.parse("2027-02-28"), end.dateAt(MonthPages.CENTER + 1, selected))
        assertEquals(LocalDate.parse("2028-02-29"), end.dateAt(MonthPages.CENTER + 13, selected))
        assertEquals(LocalDate.parse("2026-12-31"), end.dateAt(MonthPages.CENTER - 1, selected))
    }

    @Test
    fun `a quick create on another day starts at nine`() {
        val now = LocalDateTime.parse("2026-10-06T14:20:00")

        assertEquals(
            LocalDateTime.parse("2026-10-20T09:00:00"),
            MonthQuickCreate.startFor(LocalDate.parse("2026-10-20"), now)
        )
    }

    @Test
    fun `a quick create today starts at the next full hour`() {
        val now = LocalDateTime.parse("2026-10-06T14:20:00")

        assertEquals(
            LocalDateTime.parse("2026-10-06T15:00:00"),
            MonthQuickCreate.startFor(LocalDate.parse("2026-10-06"), now)
        )
    }

    @Test
    fun `a quick create late today stays on the day`() {
        val now = LocalDateTime.parse("2026-10-06T23:40:00")

        assertEquals(
            LocalDateTime.parse("2026-10-06T23:00:00"),
            MonthQuickCreate.startFor(LocalDate.parse("2026-10-06"), now)
        )
    }

    private companion object {
        const val MONTHS_PER_YEAR = 12
    }
}
