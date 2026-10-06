// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.month

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MonthOverflowTest {
    private val monday = LocalDate.parse("2026-10-05")
    private val days = List(MonthGrid.DAYS_IN_WEEK) { monday.plusDays(it.toLong()) }
    private var lastId = 0L

    private fun bar(name: String, firstCol: Int, lastCol: Int, lane: Int): MonthBar {
        val id = ++lastId
        return MonthBar(
            instance = EventInstance(
                EventId(id),
                CalendarId(1),
                name,
                EventTime.AllDay(monday, monday.plusDays(1))
            ),
            firstCol = firstCol,
            lastCol = lastCol,
            lane = lane,
            style = MonthBarStyle.BAR,
            color = null,
            continuesBefore = false,
            continuesAfter = false
        )
    }

    private fun fit(lanes: Int, vararg bars: MonthBar) =
        MonthOverflow.fit(MonthWeek(days, bars.toList()), lanes)

    private fun WeekFit.names() = visible.map { it.instance.title }

    @Test
    fun `when everything fits nothing is hidden`() {
        val fit = fit(3, bar("a", 0, 0, 0), bar("b", 0, 0, 1), bar("c", 0, 0, 2))

        assertEquals(listOf("a", "b", "c"), fit.names())
        assertEquals(List(MonthGrid.DAYS_IN_WEEK) { 0 }, fit.more)
    }

    @Test
    fun `a full column shows one event less and counts the rest`() {
        val fit = fit(
            3,
            bar("a", 0, 0, 0),
            bar("b", 0, 0, 1),
            bar("c", 0, 0, 2),
            bar("d", 0, 0, 3),
            bar("e", 1, 1, 0)
        )

        // Column 0 has four events in three lanes: two shown and "+2 more" in the last lane.
        assertEquals(listOf("a", "b", "e"), fit.names())
        assertEquals(2, fit.more[0])
        assertEquals(0, fit.more[1])
    }

    @Test
    fun `a bar over a column with a more is hidden in all its columns`() {
        val fit = fit(
            2,
            bar("a", 0, 0, 0),
            bar("wide", 0, 2, 1),
            bar("x", 0, 0, 2)
        )

        // "x" does not fit, "+N" needs the last lane of column 0, where "wide" is: it hides too
        // and shows up in the counts of the columns it covers.
        assertEquals(listOf("a"), fit.names())
        assertEquals(listOf(2, 1, 1), fit.more.take(3))
    }

    @Test
    fun `columns that do not overflow keep their last lane`() {
        val fit = fit(
            2,
            bar("a", 0, 0, 0),
            bar("b", 0, 0, 1),
            bar("c", 0, 0, 2),
            bar("other", 3, 3, 1)
        )

        assertEquals(listOf("a", "other"), fit.names())
        assertEquals(listOf(2, 0, 0, 0), fit.more.take(4))
    }

    @Test
    fun `a hidden bar hides what shares its overflowing column in the reserved lane`() {
        val fit = fit(
            2,
            bar("hidden", 1, 3, 2),
            bar("low", 0, 0, 0),
            bar("reserved", 3, 5, 1)
        )

        // "reserved" shares column 3 with the hidden bar, so it hides as well.
        assertEquals(listOf("low"), fit.names())
        assertEquals(listOf(0, 1, 1, 2, 1, 1, 0), fit.more)
    }

    @Test
    fun `one lane shows only the count when a column holds more than one event`() {
        val fit = fit(1, bar("a", 0, 0, 0), bar("b", 0, 0, 1), bar("solo", 1, 1, 0))

        assertEquals(listOf("solo"), fit.names())
        assertEquals(2, fit.more[0])
    }

    @Test
    fun `without lanes everything is counted`() {
        val bars = arrayOf(bar("a", 0, 1, 0), bar("b", 1, 1, 1))

        assertEquals(listOf(1, 2, 0, 0, 0, 0, 0), fit(0, *bars).more)
        assertTrue(fit(0, *bars).visible.isEmpty())
        assertEquals(fit(0, *bars), fit(-3, *bars))
    }

    @Test
    fun `an empty week fits`() {
        val fit = fit(3)

        assertTrue(fit.visible.isEmpty())
        assertEquals(List(MonthGrid.DAYS_IN_WEEK) { 0 }, fit.more)
    }
}
