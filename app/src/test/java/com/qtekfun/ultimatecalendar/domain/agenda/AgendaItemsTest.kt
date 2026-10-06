// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.agenda

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.LocalDate
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgendaItemsTest {
    private fun day(date: String, vararg titles: String) = AgendaDay(
        LocalDate.parse(date),
        titles.mapIndexed { index, title ->
            AgendaEntry(
                EventInstance(
                    EventId(index + 1L),
                    CalendarId(1),
                    title,
                    EventTime.AllDay(LocalDate.parse(date), LocalDate.parse(date).plusDays(1))
                ),
                AgendaSlot.AllDay,
                color = null,
                otherZone = null
            )
        }
    )

    private val days = listOf(
        day("2026-10-29", "a", "b"),
        day("2026-10-30", "c"),
        day("2026-11-02", "d")
    )

    @Test
    fun `no days give no rows`() {
        assertTrue(AgendaItems.flatten(emptyList()).isEmpty())
    }

    @Test
    fun `rows are a month divider when the month starts, then header and events`() {
        val items = AgendaItems.flatten(days)

        assertEquals(
            listOf(
                "m-2026-10",
                "d-${LocalDate.parse("2026-10-29").toEpochDay()}",
                "e-${LocalDate.parse("2026-10-29").toEpochDay()}-1-0",
                "e-${LocalDate.parse("2026-10-29").toEpochDay()}-2-1",
                "d-${LocalDate.parse("2026-10-30").toEpochDay()}",
                "e-${LocalDate.parse("2026-10-30").toEpochDay()}-1-0",
                "m-2026-11",
                "d-${LocalDate.parse("2026-11-02").toEpochDay()}",
                "e-${LocalDate.parse("2026-11-02").toEpochDay()}-1-0"
            ),
            items.map { it.key }
        )
        assertEquals(
            YearMonth.of(2026, 11),
            (items[6] as AgendaItem.MonthDivider).month
        )
    }

    @Test
    fun `keys are all different`() {
        val items = AgendaItems.flatten(days)

        assertEquals(items.size, items.map { it.key }.toSet().size)
    }

    @Test
    fun `scrolling to a day with events lands on its month divider when it opens the month`() {
        val items = AgendaItems.flatten(days)

        assertEquals(0, AgendaItems.scrollIndex(items, LocalDate.parse("2026-10-29")))
        assertEquals(6, AgendaItems.scrollIndex(items, LocalDate.parse("2026-11-02")))
    }

    @Test
    fun `scrolling to a day inside a month lands on its header`() {
        val items = AgendaItems.flatten(days)

        assertEquals(4, AgendaItems.scrollIndex(items, LocalDate.parse("2026-10-30")))
    }

    @Test
    fun `scrolling to a day without events lands on the next day that has some`() {
        val items = AgendaItems.flatten(days)

        assertEquals(6, AgendaItems.scrollIndex(items, LocalDate.parse("2026-10-31")))
    }

    @Test
    fun `scrolling past the last day lands on the last row`() {
        val items = AgendaItems.flatten(days)

        assertEquals(items.lastIndex, AgendaItems.scrollIndex(items, LocalDate.parse("2027-01-01")))
        assertEquals(0, AgendaItems.scrollIndex(emptyList(), LocalDate.parse("2027-01-01")))
    }

    @Test
    fun `every row knows its day`() {
        val items = AgendaItems.flatten(days)

        assertEquals(LocalDate.parse("2026-10-30"), AgendaItems.dateAt(items, 5))
        assertEquals(LocalDate.parse("2026-11-02"), AgendaItems.dateAt(items, 6))
        assertNull(AgendaItems.dateAt(items, 99))
        assertNull(AgendaItems.dateAt(items, -1))
    }
}
