// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalendarSettingsTest {
    private val calendar = CalendarInfo(
        CalendarId(1),
        CalendarAccount("me@example.com", "com.google"),
        "Mine",
        0xFF0B63CE.toInt(),
        CalendarAccess.OWNER
    )

    @Test
    fun `empty settings leave the calendar as the source gave it`() {
        assertTrue(CalendarSettings().isEmpty)
        assertEquals(calendar, CalendarSettings().applyTo(calendar))
    }

    @Test
    fun `each override replaces only its own field`() {
        val settings =
            CalendarSettings(displayName = "Work", color = 0xFF00FF00.toInt(), visible = false)

        assertFalse(settings.isEmpty)
        assertEquals(
            calendar.copy(displayName = "Work", color = 0xFF00FF00.toInt(), visible = false),
            settings.applyTo(calendar)
        )
        assertEquals(
            calendar.copy(displayName = "Work"),
            CalendarSettings(displayName = "Work").applyTo(calendar)
        )
        assertEquals(
            calendar.copy(visible = false),
            CalendarSettings(visible = false).applyTo(calendar)
        )
    }

    @Test
    fun `a single override is enough to make the settings non-empty`() {
        assertFalse(CalendarSettings(color = 1).isEmpty)
        assertFalse(CalendarSettings(visible = true).isEmpty)
    }

    @Test
    fun `a new name and color become overrides, the visibility stays`() {
        val look = CalendarSettings(visible = false).withLook("Work", " Job ", 1, 2)

        assertEquals(CalendarSettings("Job", 2, false), look)
    }

    @Test
    fun `what was shown and not changed keeps the stored override or none`() {
        assertEquals(CalendarSettings(), CalendarSettings().withLook("Work", "Work", 1, 1))
        assertEquals(
            CalendarSettings("Work", 1),
            CalendarSettings("Work", 1).withLook("Work", "Work", 1, 1)
        )
    }

    @Test
    fun `a blank name and no color go back to the source's`() {
        assertEquals(
            CalendarSettings(visible = true),
            CalendarSettings("Work", 1, true).withLook("Work", "  ", 1, null)
        )
    }
}
