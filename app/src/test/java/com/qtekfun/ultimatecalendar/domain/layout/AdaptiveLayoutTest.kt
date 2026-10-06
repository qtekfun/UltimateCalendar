// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.layout

import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdaptiveLayoutTest {
    private val phone = AdaptiveLayout.of(411)
    private val smallTablet = AdaptiveLayout.of(600)
    private val tablet = AdaptiveLayout.of(1280)

    @Test
    fun `window widths split at 600 and 840 dp`() {
        assertEquals(WidthClass.COMPACT, WidthClass.of(0))
        assertEquals(WidthClass.COMPACT, WidthClass.of(599))
        assertEquals(WidthClass.MEDIUM, WidthClass.of(600))
        assertEquals(WidthClass.MEDIUM, WidthClass.of(839))
        assertEquals(WidthClass.EXPANDED, WidthClass.of(840))
    }

    @Test
    fun `only expanded windows get the permanent drawer`() {
        assertEquals(NavigationStyle.MODAL_DRAWER, phone.navigation)
        assertEquals(NavigationStyle.MODAL_DRAWER, smallTablet.navigation)
        assertEquals(NavigationStyle.PERMANENT_DRAWER, AdaptiveLayout.of(840).navigation)
    }

    @Test
    fun `the layout depends on the width alone, whatever size came before`() {
        // Going 1280 -> 411 -> 1280 (rotation, split screen, fold) needs no hysteresis: a width
        // just under a threshold and the threshold itself always differ, in both directions.
        val widths = listOf(1280, 411, 840, 839, 600, 599, 840, 1280)
        val classes = widths.map { AdaptiveLayout.of(it).widthClass }
        val (e, m, c) = Triple(WidthClass.EXPANDED, WidthClass.MEDIUM, WidthClass.COMPACT)
        val expected = listOf(e, c, e, m, m, c, e, e)
        assertEquals(expected, classes)
    }

    @Test
    fun `search and the tray are dialogs only on expanded windows`() {
        assertFalse(phone.overlaysAsDialogs)
        assertFalse(smallTablet.overlaysAsDialogs)
        assertTrue(tablet.overlaysAsDialogs)
    }

    @Test
    fun `the agenda has two panes only when expanded`() {
        assertFalse(phone.agendaTwoPane)
        assertFalse(smallTablet.agendaTwoPane)
        assertTrue(tablet.agendaTwoPane)
    }

    @Test
    fun `the detail goes in the pane only for the agenda, wide, with nothing on top`() {
        assertTrue(tablet.detailInPane(CalendarView.AGENDA, overlayOpen = false))
        assertFalse(tablet.detailInPane(CalendarView.AGENDA, overlayOpen = true))
        assertFalse(tablet.detailInPane(CalendarView.WEEK, overlayOpen = false))
        assertFalse(tablet.detailInPane(CalendarView.MONTH, overlayOpen = false))
        assertFalse(smallTablet.detailInPane(CalendarView.AGENDA, overlayOpen = false))
        assertFalse(phone.detailInPane(CalendarView.AGENDA, overlayOpen = false))
    }

    @Test
    fun `phones keep the 840 dp cap on every view`() {
        CalendarView.entries.forEach {
            assertEquals(AdaptiveLayout.WIDE_MAX_DP, phone.contentMaxWidthDp(it))
        }
    }

    @Test
    fun `a day and a plain agenda stay capped on a medium window, the rest fills it`() {
        assertEquals(840, smallTablet.contentMaxWidthDp(CalendarView.DAY))
        assertEquals(840, smallTablet.contentMaxWidthDp(CalendarView.AGENDA))
        assertNull(smallTablet.contentMaxWidthDp(CalendarView.THREE_DAYS))
        assertNull(smallTablet.contentMaxWidthDp(CalendarView.WEEK))
        assertNull(smallTablet.contentMaxWidthDp(CalendarView.MONTH))
    }

    @Test
    fun `on an expanded window only the day stays capped`() {
        assertEquals(840, tablet.contentMaxWidthDp(CalendarView.DAY))
        assertNull(tablet.contentMaxWidthDp(CalendarView.AGENDA))
        assertNull(tablet.contentMaxWidthDp(CalendarView.THREE_DAYS))
        assertNull(tablet.contentMaxWidthDp(CalendarView.WEEK))
        assertNull(tablet.contentMaxWidthDp(CalendarView.MONTH))
    }

    @Test
    fun `the grid grows with the window`() {
        assertEquals(1f, phone.gridScale)
        assertEquals(1.15f, smallTablet.gridScale)
        assertEquals(1.3f, tablet.gridScale)
    }

    @Test
    fun `the grid text factor never takes the font past 200 percent`() {
        assertEquals(1f, phone.gridTextFactor(1f))
        assertEquals(1.3f, tablet.gridTextFactor(1f))
        assertEquals(1.3f, tablet.gridTextFactor(1.5f))
        assertEquals(1.25f, tablet.gridTextFactor(1.6f), 1e-6f)
        assertEquals(1f, tablet.gridTextFactor(2f))
        // Past the limit the user's own scale stays as it is: the factor never shrinks it.
        assertEquals(1f, tablet.gridTextFactor(2.5f))
    }
}
