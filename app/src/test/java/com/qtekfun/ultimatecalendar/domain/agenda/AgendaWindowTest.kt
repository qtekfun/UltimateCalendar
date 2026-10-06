// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.agenda

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgendaWindowTest {
    private val anchor = LocalDate.parse("2026-10-06")

    @Test
    fun `the first window starts a little before the anchor and goes a month after it`() {
        val window = AgendaWindow.around(anchor)

        assertEquals(anchor, window.anchor)
        assertEquals(anchor.minusDays(14), window.range.start)
        assertEquals(anchor.plusDays(30), window.range.endExclusive)
        assertTrue(anchor in window)
        assertFalse(anchor.minusDays(15) in window)
    }

    @Test
    fun `earlier adds a chunk before and leaves the end alone`() {
        val window = AgendaWindow.around(anchor).earlier()

        assertEquals(anchor.minusDays(44), window.range.start)
        assertEquals(anchor.plusDays(30), window.range.endExclusive)
    }

    @Test
    fun `later adds a chunk after and leaves the start alone`() {
        val window = AgendaWindow.around(anchor).later()

        assertEquals(anchor.minusDays(14), window.range.start)
        assertEquals(anchor.plusDays(60), window.range.endExclusive)
    }

    @Test
    fun `earlier stops at a year before the anchor`() {
        var window = AgendaWindow.around(anchor)
        repeat(20) { window = window.earlier() }

        assertEquals(anchor.minusDays(366), window.range.start)
        assertFalse(window.canExtendEarlier)
        assertEquals(window, window.earlier())
        assertTrue(window.canExtendLater)
    }

    @Test
    fun `later stops at a year after the anchor`() {
        var window = AgendaWindow.around(anchor)
        repeat(20) { window = window.later() }

        assertEquals(anchor.plusDays(366), window.range.endExclusive)
        assertFalse(window.canExtendLater)
        assertEquals(window, window.later())
        assertTrue(window.canExtendEarlier)
    }

    @Test
    fun `a fresh window can grow both ways`() {
        val window = AgendaWindow.around(anchor)

        assertTrue(window.canExtendEarlier && window.canExtendLater)
    }
}
