// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RecurrenceRulesTest {
    @Test
    fun `reads every supported part`() {
        assertEquals(
            RecurrenceRule(
                frequency = Frequency.MONTHLY,
                interval = 2,
                byDay = listOf(
                    WeekdayNum(SATURDAY),
                    WeekdayNum(TUESDAY, 2),
                    WeekdayNum(FRIDAY, -1)
                ),
                byMonthDay = listOf(1, -1),
                byMonth = listOf(3, 12),
                bySetPos = listOf(-1),
                count = 5,
                until = LocalDate.parse("2027-01-31"),
                weekStart = SUNDAY
            ),
            RecurrenceRules.parse(
                "RRULE:freq=monthly;INTERVAL=2;BYDAY=SA,2TU,-1FR;BYMONTHDAY=1,-1;BYMONTH=3,12;" +
                    "BYSETPOS=-1;COUNT=5;UNTIL=20270131T225959Z;WKST=SU;"
            )
        )
    }

    @Test
    fun `defaults`() {
        assertEquals(RecurrenceRule(Frequency.DAILY), RecurrenceRules.parse("FREQ=DAILY"))
        assertEquals(
            LocalDate.parse("2027-01-31"),
            RecurrenceRules.parse("FREQ=DAILY;UNTIL=20270131")!!.until
        )
    }

    @Test
    fun `rules the app cannot follow are not read`() {
        listOf(
            "", "INTERVAL=2", "FREQ=HOURLY", "FREQ=DAILY;BYHOUR=9", "FREQ=DAILY;INTERVAL=0",
            "FREQ=WEEKLY;BYDAY=XX", "FREQ=MONTHLY;BYDAY=0MO", "FREQ=MONTHLY;BYMONTHDAY=0",
            "FREQ=DAILY;COUNT=0", "FREQ=DAILY;UNTIL=tomorrow", "FREQ=DAILY;WKST=XX", "FREQ"
        ).forEach { assertNull(RecurrenceRules.parse(it), it) }
    }

    @Test
    fun `writes back what it reads`() {
        listOf(
            "FREQ=DAILY",
            "FREQ=WEEKLY;BYDAY=TU,TH",
            "FREQ=MONTHLY;BYDAY=SA;BYSETPOS=-1",
            "FREQ=MONTHLY;INTERVAL=3;BYDAY=2TU,-1FR;BYMONTHDAY=13;BYMONTH=1,7;COUNT=4;UNTIL=20270131;WKST=SU"
        ).forEach { assertEquals(it, RecurrenceRules.format(RecurrenceRules.parse(it)!!)) }
    }
}
