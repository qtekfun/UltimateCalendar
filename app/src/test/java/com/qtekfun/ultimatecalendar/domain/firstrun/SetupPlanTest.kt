// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.firstrun

import com.qtekfun.ultimatecalendar.domain.firstrun.SetupItem.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SetupPlanTest {
    private val nothingAllowed = SetupStatus(
        calendarPermission = false,
        notifications = false,
        exactAlarms = false,
        batteryExempt = false,
        hasCalendars = null,
        maker = PhoneMaker.OTHER
    )

    private val allAllowed = nothingAllowed.copy(
        calendarPermission = true,
        notifications = true,
        exactAlarms = true,
        batteryExempt = true,
        hasCalendars = true
    )

    @Test
    fun `a fresh phone needs every check, in the order they are shown`() {
        assertEquals(
            listOf(
                SetupItem(SetupStep.CALENDAR_PERMISSION, State.TODO),
                SetupItem(SetupStep.NOTIFICATIONS, State.TODO),
                SetupItem(SetupStep.EXACT_ALARMS, State.TODO),
                SetupItem(SetupStep.BATTERY, State.TODO),
                SetupItem(SetupStep.AUTOSTART, State.ADVICE),
                SetupItem(SetupStep.TEST_REMINDER, State.ADVICE)
            ),
            SetupPlan.of(nothingAllowed)
        )
    }

    @Test
    fun `steps are ordered as the enum declares them`() {
        val everything = allAllowed.copy(
            hasCalendars = false,
            otherCalendarApps = listOf("com.google.android.calendar")
        )
        val steps = SetupPlan.of(everything).map { it.step }
        assertEquals(SetupStep.entries, steps)
    }

    @Test
    fun `granted permissions are done and no longer pending`() {
        val status = nothingAllowed.copy(calendarPermission = true, notifications = true)
        val items = SetupPlan.of(status).associate { it.step to it.state }
        assertEquals(State.DONE, items[SetupStep.CALENDAR_PERMISSION])
        assertEquals(State.DONE, items[SetupStep.NOTIFICATIONS])
        assertEquals(State.TODO, items[SetupStep.EXACT_ALARMS])
        assertEquals(
            listOf(SetupStep.EXACT_ALARMS, SetupStep.BATTERY),
            SetupPlan.pending(status)
        )
    }

    @Test
    fun `nothing is pending once everything is allowed`() {
        assertEquals(emptyList<SetupStep>(), SetupPlan.pending(allAllowed))
    }

    @Test
    fun `explains how to add an account only when it can see there are no calendars`() {
        val noCalendars = allAllowed.copy(hasCalendars = false)
        assertEquals(
            listOf(SetupStep.ADD_ACCOUNT),
            SetupPlan.pending(noCalendars)
        )
        assertEquals(
            SetupStep.ADD_ACCOUNT,
            SetupPlan.of(noCalendars)[1].step
        )
        // Unknown without the permission: the permission step comes first.
        assertEquals(
            false,
            SetupPlan.of(noCalendars.copy(calendarPermission = false, hasCalendars = false))
                .any { it.step == SetupStep.ADD_ACCOUNT }
        )
        assertEquals(
            false,
            SetupPlan.of(allAllowed.copy(hasCalendars = null)).any {
                it.step == SetupStep.ADD_ACCOUNT
            }
        )
    }

    @Test
    fun `other calendar apps are advice, shown only when there are some`() {
        val withApps = allAllowed.copy(otherCalendarApps = listOf("ws.xsoh.etar"))
        assertEquals(
            State.ADVICE,
            SetupPlan.of(withApps).single { it.step == SetupStep.OTHER_CALENDAR_APPS }.state
        )
        assertEquals(
            false,
            SetupPlan.of(allAllowed).any { it.step == SetupStep.OTHER_CALENDAR_APPS }
        )
        assertEquals(emptyList<SetupStep>(), SetupPlan.pending(withApps))
    }

    @Test
    fun `the maker's advice and the test are always offered last`() {
        PhoneMaker.entries.forEach { maker ->
            val steps = SetupPlan.of(allAllowed.copy(maker = maker)).map { it.step }
            assertEquals(SetupStep.TEST_REMINDER, steps.last(), "$maker")
            assertEquals(true, SetupStep.AUTOSTART in steps, "$maker")
        }
    }
}
