// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.detail

import com.qtekfun.ultimatecalendar.domain.recurrence.Frequency
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RepeatDescriberTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun describe(rule: String?) = RepeatDescriber.describe(rule, madrid)

    @Test
    fun `an event that does not repeat says nothing`() {
        assertEquals(emptyList<RepeatPhrase>(), describe(null))
    }

    @Test
    fun `a rule the app cannot read is custom`() {
        assertEquals(listOf(RepeatPhrase.Custom), describe("FREQ=DAILY;BYHOUR=9"))
    }

    @Test
    fun `plain rules keep their interval`() {
        assertEquals(listOf(RepeatPhrase.Every(Frequency.DAILY, 1)), describe("FREQ=DAILY"))
        assertEquals(
            listOf(RepeatPhrase.Every(Frequency.MONTHLY, 3)),
            describe("FREQ=MONTHLY;INTERVAL=3")
        )
        assertEquals(listOf(RepeatPhrase.Every(Frequency.YEARLY, 1)), describe("FREQ=YEARLY"))
    }

    @Test
    fun `weekly days come in week order without repeats`() {
        assertEquals(
            listOf(
                RepeatPhrase.Every(Frequency.WEEKLY, 2),
                RepeatPhrase.OnWeekdays(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
            ),
            describe("FREQ=WEEKLY;INTERVAL=2;BYDAY=WE,MO,WE")
        )
    }

    @Test
    fun `monday to friday every week is every weekday`() {
        assertEquals(
            listOf(RepeatPhrase.EveryWeekday),
            describe("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR")
        )
    }

    @Test
    fun `monday to friday every second week is not every weekday`() {
        assertEquals(
            listOf(
                RepeatPhrase.Every(Frequency.WEEKLY, 2),
                RepeatPhrase.OnWeekdays(
                    listOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY
                    )
                )
            ),
            describe("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TU,WE,TH,FR")
        )
    }

    @Test
    fun `daily rules can limit the weekdays`() {
        assertEquals(
            listOf(
                RepeatPhrase.Every(Frequency.DAILY, 1),
                RepeatPhrase.OnWeekdays(listOf(DayOfWeek.SATURDAY))
            ),
            describe("FREQ=DAILY;BYDAY=SA")
        )
    }

    @Test
    fun `monthly on days of the month`() {
        assertEquals(
            listOf(
                RepeatPhrase.Every(Frequency.MONTHLY, 1),
                RepeatPhrase.OnMonthDays(listOf(1, 15, -1))
            ),
            describe("FREQ=MONTHLY;BYMONTHDAY=1,15,-1")
        )
    }

    @Test
    fun `monthly on the third friday and on the last monday`() {
        assertEquals(
            listOf(
                RepeatPhrase.Every(Frequency.MONTHLY, 1),
                RepeatPhrase.OnOrdinalWeekday(3, DayOfWeek.FRIDAY)
            ),
            describe("FREQ=MONTHLY;BYDAY=3FR")
        )
        assertEquals(
            listOf(
                RepeatPhrase.Every(Frequency.MONTHLY, 1),
                RepeatPhrase.OnOrdinalWeekday(-1, DayOfWeek.MONDAY)
            ),
            describe("FREQ=MONTHLY;BYDAY=MO;BYSETPOS=-1")
        )
    }

    @Test
    fun `monthly weekdays without a position are just weekdays`() {
        assertEquals(
            listOf(
                RepeatPhrase.Every(Frequency.MONTHLY, 1),
                RepeatPhrase.OnWeekdays(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))
            ),
            describe("FREQ=MONTHLY;BYDAY=MO,TU")
        )
    }

    @Test
    fun `yearly on a date, on a weekday of a month and in some months`() {
        assertEquals(
            listOf(
                RepeatPhrase.Every(Frequency.YEARLY, 1),
                RepeatPhrase.OnDate(Month.MARCH, 3)
            ),
            describe("FREQ=YEARLY;BYMONTH=3;BYMONTHDAY=3")
        )
        assertEquals(
            listOf(
                RepeatPhrase.Every(Frequency.YEARLY, 1),
                RepeatPhrase.OrdinalWeekdayOfMonth(3, DayOfWeek.FRIDAY, Month.MARCH)
            ),
            describe("FREQ=YEARLY;BYMONTH=3;BYDAY=3FR")
        )
        assertEquals(
            listOf(
                RepeatPhrase.Every(Frequency.YEARLY, 1),
                RepeatPhrase.InMonths(listOf(Month.JUNE, Month.DECEMBER))
            ),
            describe("FREQ=YEARLY;BYMONTH=6,12")
        )
    }

    @Test
    fun `the end is a count or the last day`() {
        assertEquals(
            listOf(RepeatPhrase.Every(Frequency.DAILY, 1), RepeatPhrase.Times(5)),
            describe("FREQ=DAILY;COUNT=5")
        )
        assertEquals(
            listOf(
                RepeatPhrase.Every(Frequency.DAILY, 1),
                RepeatPhrase.Until(LocalDate.parse("2026-10-31"))
            ),
            describe("FREQ=DAILY;UNTIL=20261031")
        )
    }

    @Test
    fun `a moment as the end is the day it falls on in the zone`() {
        // 23:30 UTC on the 31st is already November 1st in Madrid.
        assertEquals(
            listOf(
                RepeatPhrase.Every(Frequency.DAILY, 1),
                RepeatPhrase.Until(LocalDate.parse("2026-11-01"))
            ),
            describe("FREQ=DAILY;UNTIL=20261031T233000Z")
        )
    }
}
