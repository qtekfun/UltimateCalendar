// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OtherCalendarAppsTest {
    @Test
    fun `finds the known apps that are installed, in the order they are listed`() {
        val present = setOf("ws.xsoh.etar", "com.google.android.calendar", "org.example.other")
        val found = OtherCalendarApps { it in present }.installed()
        assertEquals(listOf("com.google.android.calendar", "ws.xsoh.etar"), found)
    }

    @Test
    fun `finds nothing on a phone without them`() {
        assertEquals(emptyList<String>(), OtherCalendarApps { false }.installed())
    }

    @Test
    fun `never reports this app`() {
        assertTrue("com.qtekfun.ultimatecalendar" !in OtherCalendarApps.KNOWN)
        assertEquals(OtherCalendarApps.KNOWN.size, OtherCalendarApps.KNOWN.toSet().size)
    }

    @Test
    fun `the manifest asks Android to show exactly the known apps`() {
        // Without a matching <queries> entry Android 11+ hides the app, so detection fails silently.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val queried = Regex("""<queries>(.*?)</queries>""", RegexOption.DOT_MATCHES_ALL)
            .find(manifest)!!.groupValues[1]
        val packages = Regex("""<package\s+android:name="([^"]+)"""")
            .findAll(queried).map { it.groupValues[1] }.toList()
        assertEquals(OtherCalendarApps.KNOWN.sorted(), packages.sorted())
    }
}
