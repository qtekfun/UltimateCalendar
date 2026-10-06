// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MakerScreensTest {
    @Test
    fun `ColorOS tries the screen of current OPPO phones first`() {
        assertEquals(
            Screen("com.oplus.battery", "com.oplus.startupapp.view.StartupAppListActivity"),
            MakerScreens.of(PhoneMaker.COLOROS).first()
        )
    }

    @Test
    fun `makers that hide auto-start have a screen of their own, inside their packages`() {
        val owners = mapOf(
            PhoneMaker.COLOROS to listOf("com.oplus.", "com.coloros.", "com.oppo."),
            PhoneMaker.VIVO to listOf("com.vivo.", "com.iqoo."),
            PhoneMaker.XIAOMI to listOf("com.miui."),
            PhoneMaker.HUAWEI to listOf("com.huawei.")
        )
        owners.forEach { (maker, prefixes) ->
            val screens = MakerScreens.of(maker)
            assertTrue(screens.isNotEmpty(), "$maker")
            screens.forEach { screen ->
                assertTrue(prefixes.any { screen.packageName.startsWith(it) }, "$screen")
                assertTrue(
                    screen.className.startsWith(screen.packageName.substringBeforeLast('.')),
                    "$screen"
                )
            }
        }
    }

    @Test
    fun `Samsung and the rest go straight to the app's info page`() {
        assertEquals(emptyList<Screen>(), MakerScreens.of(PhoneMaker.SAMSUNG))
        assertEquals(emptyList<Screen>(), MakerScreens.of(PhoneMaker.OTHER))
    }
}
