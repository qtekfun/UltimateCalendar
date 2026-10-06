// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.result

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CalendarResultTest {
    private val ok: CalendarResult<Int> = CalendarResult.Success(2)
    private val failed: CalendarResult<Int> = CalendarResult.Failure(CalendarError.ReadOnly)

    @Test
    fun `getOrNull returns the value only on success`() {
        assertEquals(2, ok.getOrNull())
        assertNull(failed.getOrNull())
    }

    @Test
    fun `map changes a value and keeps a failure`() {
        assertEquals(CalendarResult.Success(4), ok.map { it * 2 })
        assertEquals(failed, failed.map { it * 2 })
    }

    @Test
    fun `flatMap chains successes and stops at the first failure`() {
        assertEquals(CalendarResult.Success("2"), ok.flatMap { CalendarResult.Success("$it") })
        assertEquals(
            CalendarResult.Failure(CalendarError.NotFound),
            ok.flatMap { CalendarResult.Failure(CalendarError.NotFound) }
        )
        assertEquals(failed, failed.flatMap { CalendarResult.Success("$it") })
    }
}
