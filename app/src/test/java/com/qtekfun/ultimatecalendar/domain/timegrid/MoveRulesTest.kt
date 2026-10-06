// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MoveRulesTest {
    private fun calendar(id: Long, access: CalendarAccess, type: String = "com.google") =
        CalendarInfo(CalendarId(id), CalendarAccount("a@example.com", type), "C$id", 0, access)

    private fun instance(calendar: Long) = EventInstance(
        EventId(1),
        CalendarId(calendar),
        "Event",
        EventTime.AllDay(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 12))
    )

    @Test
    fun `only calendars that allow editing let their events be picked up`() {
        val rules = MoveRules.byCalendar(
            listOf(
                calendar(1, CalendarAccess.OWNER),
                calendar(2, CalendarAccess.EDIT),
                calendar(3, CalendarAccess.CONTRIBUTE),
                calendar(4, CalendarAccess.READ)
            )
        )

        assertTrue(MoveRules.canPickUp(rules, instance(1)))
        assertTrue(MoveRules.canPickUp(rules, instance(2)))
        assertFalse(MoveRules.canPickUp(rules, instance(3)))
        assertFalse(MoveRules.canPickUp(rules, instance(4)))
    }

    @Test
    fun `an event of an unknown calendar cannot be picked up`() {
        assertFalse(MoveRules.canPickUp(emptyMap(), instance(9)))
    }

    @Test
    fun `a series of a local calendar cannot have one occurrence changed`() {
        val local = MoveRules.of(calendar(1, CalendarAccess.OWNER, type = "LOCAL"))
        val google = MoveRules.of(calendar(2, CalendarAccess.OWNER))

        assertEquals(
            listOf(RecurrenceScope.THIS_AND_FOLLOWING, RecurrenceScope.ALL),
            local.scopes
        )
        assertEquals(RecurrenceScope.entries, google.scopes)
    }
}
