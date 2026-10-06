// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CalendarAccessTest {
    @Test
    fun `what each level allows`() {
        fun allowed(access: CalendarAccess) =
            Triple(access.canRespond, access.canCreate, access.canEdit)

        assertEquals(Triple(false, false, false), allowed(CalendarAccess.NONE))
        assertEquals(Triple(false, false, false), allowed(CalendarAccess.FREE_BUSY))
        assertEquals(Triple(false, false, false), allowed(CalendarAccess.READ))
        assertEquals(Triple(true, false, false), allowed(CalendarAccess.RESPOND))
        assertEquals(Triple(true, true, false), allowed(CalendarAccess.CONTRIBUTE))
        assertEquals(Triple(true, true, true), allowed(CalendarAccess.EDIT))
        assertEquals(Triple(true, true, true), allowed(CalendarAccess.OWNER))
    }
}
