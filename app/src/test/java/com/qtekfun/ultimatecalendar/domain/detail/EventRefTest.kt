// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.detail

import com.qtekfun.ultimatecalendar.domain.model.EventId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EventRefTest {
    @Test
    fun `a ref survives being written and read`() {
        val ref = EventRef(EventId(42), 1_790_000_000_000, 1_790_003_600_000, allDay = true)
        assertEquals(ref, EventRef.decode(ref.encode()))
    }

    @Test
    fun `anything else is not a ref`() {
        assertNull(EventRef.decode(null))
        assertNull(EventRef.decode(""))
        assertNull(EventRef.decode("1:2:3"))
        assertNull(EventRef.decode("a:2:3:true"))
        assertNull(EventRef.decode("1:2:3:maybe"))
    }
}
