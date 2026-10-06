// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReminderLinksTest {
    @Test
    fun `finds the video calls of the usual services`() {
        listOf(
            "https://meet.google.com/abc-defg-hij",
            "https://us02web.zoom.us/j/123456?pwd=x",
            "https://zoom.us/j/1",
            "https://teams.microsoft.com/l/meetup-join/xyz",
            "https://meet.jit.si/Room",
            "https://jitsi.example.org/Room",
            "HTTPS://MEET.GOOGLE.COM/ABC",
            "https://user@zoom.us:443/j/1"
        ).forEach { assertEquals(it, ReminderLinks.videoCall(it), it) }
    }

    @Test
    fun `finds the link inside text and drops the punctuation after it`() {
        assertEquals(
            "https://meet.google.com/abc",
            ReminderLinks.videoCall("Join at https://meet.google.com/abc, see you (https://x.org)")
        )
    }

    @Test
    fun `ignores other links and similar host names`() {
        assertNull(ReminderLinks.videoCall("https://example.com/zoom.us", "https://notzoom.us/j"))
        assertNull(ReminderLinks.videoCall("no link", "", null))
        assertNull(ReminderLinks.videoCall())
    }

    @Test
    fun `takes the first call in the order of the texts`() {
        assertEquals(
            "https://zoom.us/j/1",
            ReminderLinks.videoCall(null, "https://zoom.us/j/1", "https://meet.google.com/a")
        )
        assertEquals(
            "https://meet.google.com/a",
            ReminderLinks.videoCall("https://example.com", "https://meet.google.com/a")
        )
    }

    @Test
    fun `a place becomes a map search`() {
        assertEquals(
            "geo:0,0?q=Calle%20Mayor%201%2C%20Madrid",
            ReminderLinks.map(" Calle Mayor 1, Madrid ")
        )
    }

    @Test
    fun `no map for an empty place or a web link`() {
        assertNull(ReminderLinks.map(null))
        assertNull(ReminderLinks.map("  "))
        assertNull(ReminderLinks.map("https://meet.google.com/abc"))
        assertNull(ReminderLinks.map("Zoom https://zoom.us/j/1"))
    }
}
