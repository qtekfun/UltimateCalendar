// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.ui.theme.Motion
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComponentLogicTest {
    private val week = PeriodKey(CalendarView.WEEK, LocalDate.of(2026, 10, 6))

    @Test
    fun `later periods slide forward and earlier ones backward`() {
        assertEquals(
            PeriodMotion.FORWARD,
            periodMotion(week, week.copy(date = week.date.plusDays(7)))
        )
        assertEquals(
            PeriodMotion.BACKWARD,
            periodMotion(week, week.copy(date = week.date.minusDays(1)))
        )
    }

    @Test
    fun `another view fades through even when the date changes too`() {
        val month = week.copy(view = CalendarView.MONTH, date = week.date.plusDays(30))
        assertEquals(PeriodMotion.SWITCH, periodMotion(week, month))
    }

    @Test
    fun `the same period does not move`() {
        assertEquals(PeriodMotion.NONE, periodMotion(week, week.copy()))
    }

    @Test
    fun `window widths split at 600 and 840 dp`() {
        assertEquals(WindowWidth.COMPACT, WindowWidth.of(411))
        assertEquals(WindowWidth.COMPACT, WindowWidth.of(599))
        assertEquals(WindowWidth.MEDIUM, WindowWidth.of(600))
        assertEquals(WindowWidth.MEDIUM, WindowWidth.of(839))
        assertEquals(WindowWidth.EXPANDED, WindowWidth.of(840))
    }

    @Test
    fun `the create button starts expanded and collapses after scrolling down`() {
        val tracker = FabScrollTracker(threshold = 10f)
        assertTrue(tracker.expanded)
        tracker.onScroll(6f)
        assertTrue(tracker.expanded)
        tracker.onScroll(6f)
        assertFalse(tracker.expanded)
    }

    @Test
    fun `scrolling back up expands it again`() {
        val tracker = FabScrollTracker(threshold = 10f)
        tracker.onScroll(40f)
        assertFalse(tracker.expanded)
        tracker.onScroll(-5f)
        assertFalse(tracker.expanded)
        tracker.onScroll(-8f)
        assertTrue(tracker.expanded)
    }

    @Test
    fun `a jitter that changes direction never flips the button`() {
        val tracker = FabScrollTracker(threshold = 10f)
        repeat(20) {
            tracker.onScroll(4f)
            tracker.onScroll(-4f)
        }
        assertTrue(tracker.expanded)
    }

    @Test
    fun `reset and zero scroll`() {
        val tracker = FabScrollTracker(threshold = 10f)
        tracker.onScroll(50f)
        tracker.onScroll(0f)
        assertFalse(tracker.expanded)
        tracker.reset()
        assertTrue(tracker.expanded)
    }

    @Test
    fun `animations count as removed when the animator scale is zero`() {
        assertTrue(Motion.isReduced(0f))
        assertFalse(Motion.isReduced(1f))
        assertFalse(Motion.isReduced(0.5f))
    }
}
