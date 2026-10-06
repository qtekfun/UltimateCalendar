// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WeekNumbersTest {
    @Test
    fun `Monday weeks match ISO 8601`() {
        assertEquals(41, WeekNumbers.of(LocalDate.of(2026, 10, 6), DayOfWeek.MONDAY))
        // 1 January 2027 is a Friday: still the last week of 2026 (week 53).
        assertEquals(53, WeekNumbers.of(LocalDate.of(2027, 1, 1), DayOfWeek.MONDAY))
        // 29 December 2025 is a Monday whose week holds 4 days of 2026: week 1.
        assertEquals(1, WeekNumbers.of(LocalDate.of(2025, 12, 29), DayOfWeek.MONDAY))
    }

    @Test
    fun `a Sunday start moves the boundary by a day`() {
        // Sunday 3 January 2027 ends ISO week 53 of 2026 but starts week 1 of a Sunday-first 2027.
        assertEquals(53, WeekNumbers.of(LocalDate.of(2027, 1, 3), DayOfWeek.MONDAY))
        assertEquals(1, WeekNumbers.of(LocalDate.of(2027, 1, 3), DayOfWeek.SUNDAY))
    }
}
