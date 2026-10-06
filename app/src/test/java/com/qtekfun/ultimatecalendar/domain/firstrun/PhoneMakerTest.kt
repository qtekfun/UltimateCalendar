// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhoneMakerTest {
    @Test
    fun `makers are grouped by the system they ship`() {
        assertEquals(
            listOf(
                PhoneMaker.COLOROS, PhoneMaker.COLOROS, PhoneMaker.COLOROS,
                PhoneMaker.XIAOMI, PhoneMaker.XIAOMI, PhoneMaker.HUAWEI,
                PhoneMaker.SAMSUNG, PhoneMaker.VIVO, PhoneMaker.OTHER,
                PhoneMaker.XIAOMI, PhoneMaker.HUAWEI, PhoneMaker.VIVO
            ),
            listOf(
                "OPPO", "realme", " OnePlus ", "Xiaomi", "POCO", "HONOR",
                "samsung", "vivo", "Google", "Redmi", "HUAWEI", "iQOO"
            ).map(PhoneMaker::of)
        )
    }
}
