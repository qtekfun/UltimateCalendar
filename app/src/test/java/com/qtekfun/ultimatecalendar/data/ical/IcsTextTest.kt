// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IcsTextTest {
    @Test
    fun `escapes the special characters`() {
        assertEquals("""a\\b\;c\,d\ne\nf""", IcsText.escape("a\\b;c,d\ne\r\nf"))
    }

    @Test
    fun `unescapes them back, accepting an upper case N and a trailing backslash`() {
        assertEquals("a\\b;c,d\ne\nf:\\", IcsText.unescape("""a\\b\;c\,d\ne\Nf\:\"""))
    }

    @Test
    fun `escape and unescape are inverse`() {
        val text = "Línea 1\nLínea 2; con, signos \\ raros 🎉"
        assertEquals(text, IcsText.unescape(IcsText.escape(text)))
    }
}
