// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RepeatChoicesTest {
    // Friday 16 October 2026: the third Friday of the month.
    private val friday = LocalDate.parse("2026-10-16")
    private val zone = ZoneId.of("Europe/Madrid")

    private fun rule(text: String) = RecurrenceRules.parse(text)!!

    private fun format(repeat: CustomRepeat, anchor: LocalDate = friday) =
        RecurrenceRules.format(repeat.toRule(anchor))

    @Test
    fun `presets are recognised in any order or case`() {
        assertEquals(RepeatPreset.NEVER, RepeatPreset.of(null))
        assertEquals(RepeatPreset.WEEKDAYS, RepeatPreset.of("freq=weekly;byday=MO,TU,WE,TH,FR"))
        assertEquals(RepeatPreset.QUARTERLY, RepeatPreset.of("INTERVAL=3;FREQ=MONTHLY"))
        assertNull(RepeatPreset.of("FREQ=MONTHLY;BYDAY=3FR"))
        assertNull(RepeatPreset.of("FREQ=HOURLY"))
    }

    @Test
    fun `the editor starts from the task date`() {
        val start = CustomRepeat.from(null, friday, zone)
        assertEquals(setOf(FRIDAY), start.weekdays)
        assertEquals(3 to FRIDAY, start.ordinal to start.weekday)
        // Day 30 is in the fifth week: "the last" one.
        assertEquals(-1, CustomRepeat.from(null, LocalDate.parse("2026-10-30"), zone).ordinal)
    }

    @Test
    fun `the third Friday of every month`() {
        val repeat =
            CustomRepeat(
                frequency = Frequency.MONTHLY,
                monthlyMode = MonthlyMode.WEEKDAY_OF_MONTH,
                ordinal = 3,
                weekday = FRIDAY
            )
        assertEquals("FREQ=MONTHLY;BYDAY=3FR", format(repeat))
        assertEquals(
            repeat.copy(weekdays = setOf(FRIDAY)),
            CustomRepeat.from(rule("FREQ=MONTHLY;BYDAY=3FR"), friday, zone)
        )
    }

    @Test
    fun `the last Saturday written with BYSETPOS reads as such`() {
        val read = CustomRepeat.from(rule("FREQ=MONTHLY;BYDAY=SA;BYSETPOS=-1"), friday, zone)
        assertEquals(
            Triple(MonthlyMode.WEEKDAY_OF_MONTH, -1, SATURDAY),
            Triple(read.monthlyMode, read.ordinal, read.weekday)
        )
        assertEquals("FREQ=MONTHLY;BYDAY=-1SA", format(read))
    }

    @Test
    fun `monthly on the same day, weekly on some days, daily and yearly`() {
        assertEquals(
            "FREQ=MONTHLY;INTERVAL=2",
            format(CustomRepeat(frequency = Frequency.MONTHLY, interval = 2))
        )
        assertEquals(
            MonthlyMode.DAY_OF_MONTH,
            CustomRepeat.from(rule("FREQ=MONTHLY"), friday, zone).monthlyMode
        )
        assertEquals(
            "FREQ=WEEKLY;BYDAY=MO,WE",
            format(CustomRepeat(weekdays = setOf(WEDNESDAY, MONDAY)))
        )
        assertEquals("FREQ=WEEKLY;BYDAY=FR", format(CustomRepeat()))
        assertEquals(
            setOf(MONDAY, WEDNESDAY),
            CustomRepeat.from(rule("FREQ=WEEKLY;BYDAY=MO,WE"), friday, zone).weekdays
        )
        assertEquals(
            "FREQ=DAILY;INTERVAL=3",
            format(
                CustomRepeat(frequency = Frequency.DAILY, interval = 3, weekdays = setOf(MONDAY))
            )
        )
        assertEquals(
            "FREQ=YEARLY",
            format(CustomRepeat(frequency = Frequency.YEARLY, interval = 0))
        )
    }

    @Test
    fun `endings`() {
        val until = LocalDate.parse("2027-06-30")
        assertEquals(
            "FREQ=WEEKLY;BYDAY=FR;COUNT=5",
            format(CustomRepeat(end = RepeatEnd.AFTER_COUNT, count = 5))
        )
        assertEquals(
            "FREQ=WEEKLY;BYDAY=FR;COUNT=1",
            format(CustomRepeat(end = RepeatEnd.AFTER_COUNT, count = 0))
        )
        assertEquals(
            "FREQ=WEEKLY;BYDAY=FR;UNTIL=20270630",
            format(CustomRepeat(end = RepeatEnd.ON_DATE, until = until))
        )
        assertEquals(
            "FREQ=WEEKLY;BYDAY=FR",
            format(CustomRepeat(end = RepeatEnd.NEVER, until = until, count = 3))
        )
        assertEquals(
            RepeatEnd.AFTER_COUNT to 5,
            CustomRepeat.from(rule("FREQ=DAILY;COUNT=5"), friday, zone).let {
                it.end to
                    it.count
            }
        )
        assertEquals(
            RepeatEnd.ON_DATE to until,
            CustomRepeat.from(rule("FREQ=DAILY;UNTIL=20270630"), friday, zone).let {
                it.end to
                    it.until
            }
        )
        assertEquals(RepeatEnd.NEVER, CustomRepeat.from(rule("FREQ=DAILY"), friday, zone).end)
    }

    @Test
    fun `a timed event ends on the last second of its day in its own zone`() {
        val repeat = CustomRepeat(end = RepeatEnd.ON_DATE, until = LocalDate.parse("2026-10-24"))
        // 24 October is CEST (UTC+2); 25 October is the day the clocks go back.
        assertEquals(
            Until.Moment(Instant.parse("2026-10-24T21:59:59Z")),
            repeat.toRule(friday, zone).until
        )
        val dstDay = repeat.copy(until = LocalDate.parse("2026-10-25"))
        assertEquals(
            Until.Moment(Instant.parse("2026-10-25T22:59:59Z")),
            dstDay.toRule(friday, zone).until
        )
        assertEquals(
            Until.Moment(Instant.parse("2026-10-25T06:59:59Z")),
            repeat.toRule(friday, ZoneId.of("America/Los_Angeles")).until
        )
        assertEquals(Until.Day(LocalDate.parse("2026-10-24")), repeat.toRule(friday).until)
        assertNull(repeat.copy(until = null).toRule(friday, zone).until)
    }

    @Test
    fun `a timed UNTIL reads back as the day it falls on in the event zone`() {
        val read = CustomRepeat.from(rule("FREQ=DAILY;UNTIL=20261024T215959Z"), friday, zone)
        assertEquals(LocalDate.parse("2026-10-24"), read.until)
        // The same instant is still the 24th in Madrid but already the 25th in Auckland.
        assertEquals(
            LocalDate.parse("2026-10-25"),
            CustomRepeat.from(
                rule("FREQ=DAILY;UNTIL=20261024T215959Z"),
                friday,
                ZoneId.of("Pacific/Auckland")
            ).until
        )
    }
}
