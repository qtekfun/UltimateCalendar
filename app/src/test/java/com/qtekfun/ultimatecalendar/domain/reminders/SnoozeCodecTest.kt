// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SnoozeCodecTest {
    private val reminder = PlannedReminder(
        -1_099_511_627_000,
        EventId(4),
        CalendarId(2),
        "Planning | Q4\ttab",
        "Room 1",
        Instant.parse("2026-10-05T10:30:00Z"),
        true,
        Instant.parse("2026-10-05T10:15:00Z"),
        "https://zoom.us/j/1"
    )

    @Test
    fun `a reminder survives being written and read`() {
        assertEquals(reminder, SnoozeCodec.decode(SnoozeCodec.encode(reminder)))
        val bare = reminder.copy(location = null, joinUrl = null, allDay = false, title = "")
        assertEquals(bare, SnoozeCodec.decode(SnoozeCodec.encode(bare)))
    }

    @Test
    fun `a separator inside the text cannot break the line`() {
        val tricky = reminder.copy(title = "a\u001Fb", location = "c\u001Fd")
        assertEquals(
            tricky.copy(title = "ab", location = "cd"),
            SnoozeCodec.decode(SnoozeCodec.encode(tricky))
        )
    }

    @Test
    fun `lines that are not reminders are not read`() {
        assertNull(SnoozeCodec.decode(""))
        assertNull(SnoozeCodec.decode("1\u001F2"))
        assertNull(SnoozeCodec.decode(SnoozeCodec.encode(reminder).replaceFirst("4", "x")))
        assertNull(SnoozeCodec.decode(SnoozeCodec.encode(reminder).replace("true", "maybe")))
    }
}
