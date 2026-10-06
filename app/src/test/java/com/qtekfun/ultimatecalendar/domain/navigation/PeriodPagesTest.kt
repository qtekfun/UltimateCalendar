// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PeriodPagesTest {
    private val anchor = LocalDate.parse("2026-03-11")

    @Test
    fun `the center page is the anchor and days follow one by one`() {
        val pages = PeriodPages(CalendarView.DAY, anchor)

        assertEquals(anchor, pages.dateAt(PeriodPages.CENTER))
        assertEquals(anchor.plusDays(1), pages.dateAt(PeriodPages.CENTER + 1))
        assertEquals(anchor.minusDays(2), pages.dateAt(PeriodPages.CENTER - 2))
    }

    @Test
    fun `three days move by three, as the shell does`() {
        val pages = PeriodPages(CalendarView.THREE_DAYS, anchor)

        assertEquals(anchor.plusDays(3), pages.dateAt(PeriodPages.CENTER + 1))
        assertEquals(
            ViewPeriods.shift(CalendarView.THREE_DAYS, anchor, -4),
            pages.dateAt(PeriodPages.CENTER - 4)
        )
    }

    @Test
    fun `a week moves by seven`() {
        assertEquals(
            anchor.plusDays(7),
            PeriodPages(CalendarView.WEEK, anchor).dateAt(PeriodPages.CENTER + 1)
        )
    }

    @Test
    fun `a date on a page boundary has that page`() {
        val pages = PeriodPages(CalendarView.THREE_DAYS, anchor)

        assertEquals(PeriodPages.CENTER, pages.pageOf(anchor))
        assertEquals(PeriodPages.CENTER + 2, pages.pageOf(anchor.plusDays(6)))
        assertEquals(PeriodPages.CENTER - 1, pages.pageOf(anchor.minusDays(3)))
    }

    @Test
    fun `a date between boundaries has no page`() {
        val pages = PeriodPages(CalendarView.THREE_DAYS, anchor)

        assertNull(pages.pageOf(anchor.plusDays(1)))
        assertNull(pages.pageOf(anchor.minusDays(2)))
    }

    @Test
    fun `every day is a page in the day view`() {
        assertEquals(
            PeriodPages.CENTER + 100,
            PeriodPages(CalendarView.DAY, anchor).pageOf(anchor.plusDays(100))
        )
    }

    @Test
    fun `a date beyond the last page has no page`() {
        val pages = PeriodPages(CalendarView.DAY, anchor)

        assertNull(pages.pageOf(anchor.plusDays(PeriodPages.CENTER.toLong())))
        assertNull(pages.pageOf(anchor.minusDays(PeriodPages.CENTER + 1L)))
        assertEquals(PeriodPages.COUNT - 1, pages.pageOf(anchor.plusDays(PeriodPages.CENTER - 1L)))
        assertEquals(0, pages.pageOf(anchor.minusDays(PeriodPages.CENTER.toLong())))
    }

    @Test
    fun `dateAt and pageOf agree`() {
        val pages = PeriodPages(CalendarView.THREE_DAYS, anchor)

        for (page in listOf(0, 1234, PeriodPages.CENTER, 15000, PeriodPages.COUNT - 1)) {
            assertEquals(page, pages.pageOf(pages.dateAt(page)))
        }
    }

    @Test
    fun `views of months do not page by days`() {
        assertThrows<IllegalArgumentException> { PeriodPages(CalendarView.MONTH, anchor) }
        assertThrows<IllegalArgumentException> { PeriodPages(CalendarView.AGENDA, anchor) }
    }
}
