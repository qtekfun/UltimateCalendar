// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.widget

import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.model.EventId
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WidgetTapTest {
    private val ref = EventRef(EventId(7), 1_000L, 2_000L, allDay = false)

    @Test
    fun `every tap reads back as it was written`() {
        val taps = listOf(
            WidgetTap.OpenApp,
            WidgetTap.NewEvent,
            WidgetTap.OpenDay(LocalDate.parse("2026-10-06")),
            WidgetTap.OpenDay(LocalDate.parse("1969-12-31")),
            WidgetTap.OpenEvent(ref)
        )

        taps.forEach { assertEquals(it, WidgetTap.decode(it.encode())) }
    }

    @Test
    fun `anything that is not a tap is ignored`() {
        listOf(
            null,
            "",
            "open",
            "app:1",
            "new:",
            "day",
            "day:",
            "day:abc",
            "day:99999999999999",
            "event",
            "event:1:2",
            "event:x:1:2:false"
        ).forEach { assertNull(WidgetTap.decode(it), "'$it'") }
    }

    @Test
    fun `an event tap carries the occurrence, a day tap the date`() {
        assertEquals(
            WidgetTap.OpenDay(LocalDate.parse("2026-10-06")),
            WidgetTap.decode("day:20732")
        )
        assertEquals("event:7:1000:2000:false", WidgetTap.OpenEvent(ref).encode())
    }
}
