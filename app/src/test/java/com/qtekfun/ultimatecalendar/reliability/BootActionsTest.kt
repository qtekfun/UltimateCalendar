// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.reliability

import com.qtekfun.ultimatecalendar.notify.BootActions
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.w3c.dom.Element

/** The system only wakes the app for the broadcasts the manifest declares (RF-08). */
class BootActionsTest {
    private val androidName = "http://schemas.android.com/apk/res/android" to "name"

    private fun manifestActionsOf(receiver: String): Set<String> {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val declared = document.getElementsByTagName("receiver")
        val element = (0 until declared.length).map { declared.item(it) as Element }
            .single { it.getAttributeNS(androidName.first, androidName.second) == receiver }
        val actions = element.getElementsByTagName("action")
        return (0 until actions.length)
            .map {
                (actions.item(it) as Element).getAttributeNS(androidName.first, androidName.second)
            }
            .toSet()
    }

    @Test
    fun `the receiver reacts to restart, update, clock and time zone changes`() {
        assertEquals(
            setOf(
                "android.intent.action.BOOT_COMPLETED",
                "android.intent.action.MY_PACKAGE_REPLACED",
                "android.intent.action.TIME_SET",
                "android.intent.action.TIMEZONE_CHANGED"
            ),
            BootActions.ALL
        )
    }

    @Test
    fun `only a restart and an update may start the robust mode service`() {
        assertEquals(
            setOf(
                "android.intent.action.BOOT_COMPLETED",
                "android.intent.action.MY_PACKAGE_REPLACED"
            ),
            BootActions.SERVICE
        )
    }

    @Test
    fun `the manifest delivers to the receiver every broadcast it handles, and no other`() {
        assertEquals(BootActions.ALL, manifestActionsOf(".notify.BootReceiver"))
    }
}
