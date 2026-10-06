// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.editor.EditorFixtures.madrid
import com.qtekfun.ultimatecalendar.domain.recurrence.CustomRepeat
import com.qtekfun.ultimatecalendar.domain.recurrence.Frequency
import com.qtekfun.ultimatecalendar.domain.recurrence.MonthlyMode
import com.qtekfun.ultimatecalendar.domain.recurrence.RepeatEnd
import com.qtekfun.ultimatecalendar.domain.recurrence.RepeatPreset
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RepeatSettingTest {
    private val friday3 = LocalDate.parse("2026-10-16")

    @Test
    fun `a preset stores its rule and never stores none`() {
        assertNull(RepeatSetting.NEVER.toRrule(friday3, madrid))
        assertEquals(
            "FREQ=DAILY",
            RepeatSetting.Preset(RepeatPreset.DAILY).toRrule(friday3, madrid)
        )
        assertFalse(RepeatSetting.NEVER.repeats)
        assertTrue(RepeatSetting.Preset(RepeatPreset.YEARLY).repeats)
    }

    @Test
    fun `a rule that is a preset in other words is shown as the preset`() {
        val setting = RepeatSetting.of("BYDAY=MO,TU,WE,TH,FR;FREQ=WEEKLY", friday3, madrid)

        assertEquals(RepeatSetting.Preset(RepeatPreset.WEEKDAYS), setting)
    }

    @Test
    fun `no rule is no repetition`() {
        assertEquals(RepeatSetting.NEVER, RepeatSetting.of(null, friday3, madrid))
    }

    @Test
    fun `a rule that is not a preset opens in the custom editor`() {
        val setting = RepeatSetting.of("FREQ=MONTHLY;BYDAY=3FR;COUNT=4", friday3, madrid)

        val custom = assertInstanceOf(RepeatSetting.Custom::class.java, setting).repeat
        assertEquals(Frequency.MONTHLY, custom.frequency)
        assertEquals(MonthlyMode.WEEKDAY_OF_MONTH, custom.monthlyMode)
        assertEquals(3, custom.ordinal)
        assertEquals(DayOfWeek.FRIDAY, custom.weekday)
        assertEquals(RepeatEnd.AFTER_COUNT, custom.end)
        assertEquals("FREQ=MONTHLY;BYDAY=3FR;COUNT=4", setting.toRrule(friday3, madrid))
    }

    @Test
    fun `a rule the app cannot read is kept word for word`() {
        val setting = RepeatSetting.of("FREQ=WEEKLY;BYWEEKNO=3", friday3, madrid)

        assertEquals(RepeatSetting.Unsupported("FREQ=WEEKLY;BYWEEKNO=3"), setting)
        assertEquals("FREQ=WEEKLY;BYWEEKNO=3", setting.toRrule(friday3, null))
        assertTrue(setting.repeats)
    }

    @Test
    fun `the summary fills in the weekday the rule leaves implicit`() {
        val summary = RepeatSummary.of(CustomRepeat(interval = 2), friday3)

        assertEquals(Frequency.WEEKLY, summary.frequency)
        assertEquals(2, summary.interval)
        assertEquals(listOf(DayOfWeek.FRIDAY), summary.weekdays)
    }

    @Test
    fun `the summary lists weekdays from Monday`() {
        val custom =
            CustomRepeat(weekdays = setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY))

        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY),
            RepeatSummary.of(custom, friday3).weekdays
        )
    }

    @Test
    fun `a daily summary has no weekdays`() {
        val summary = RepeatSummary.of(
            CustomRepeat(frequency = Frequency.DAILY, weekdays = setOf(DayOfWeek.MONDAY)),
            friday3
        )

        assertEquals(emptyList<DayOfWeek>(), summary.weekdays)
        assertNull(summary.monthly)
        assertNull(summary.yearlyMonth)
    }

    @Test
    fun `a monthly summary is the day number or the n-th weekday`() {
        val byDay = CustomRepeat(frequency = Frequency.MONTHLY)
        val byWeekday = CustomRepeat(
            frequency = Frequency.MONTHLY,
            monthlyMode = MonthlyMode.WEEKDAY_OF_MONTH,
            ordinal = -1,
            weekday = DayOfWeek.FRIDAY
        )

        assertEquals(MonthlyDay.OfMonth(16), RepeatSummary.of(byDay, friday3).monthly)
        assertEquals(
            MonthlyDay.Nth(-1, DayOfWeek.FRIDAY),
            RepeatSummary.of(byWeekday, friday3).monthly
        )
    }

    @Test
    fun `a yearly summary says the month and day of the event`() {
        val summary = RepeatSummary.of(CustomRepeat(frequency = Frequency.YEARLY), friday3)

        assertEquals(Month.OCTOBER, summary.yearlyMonth)
        assertEquals(16, summary.yearlyDay)
    }

    @Test
    fun `the end of the summary follows the end the user picked`() {
        val until = LocalDate.parse("2027-01-01")
        val onDate = CustomRepeat(end = RepeatEnd.ON_DATE, until = until)
        val counted = CustomRepeat(end = RepeatEnd.AFTER_COUNT, count = 3)
        val never = CustomRepeat(end = RepeatEnd.NEVER, until = until, count = 3)

        assertEquals(until, RepeatSummary.of(onDate, friday3).until)
        assertEquals(3, RepeatSummary.of(counted, friday3).count)
        assertNull(RepeatSummary.of(never, friday3).until)
        assertEquals(RepeatEnd.NEVER, RepeatSummary.of(never, friday3).end)
    }

    @Test
    fun `the monthly choices for the third Friday are the 16th and the third Friday`() {
        assertEquals(
            listOf(MonthlyDay.OfMonth(16), MonthlyDay.Nth(3, DayOfWeek.FRIDAY)),
            RepeatSummary.monthlyOptions(friday3)
        )
    }

    @Test
    fun `the last Friday is offered as the last one, and never as the fifth`() {
        // 2026-10-30 is the last Friday of October and the fifth one.
        val last = LocalDate.parse("2026-10-30")

        assertEquals(
            listOf(MonthlyDay.OfMonth(30), MonthlyDay.Nth(-1, DayOfWeek.FRIDAY)),
            RepeatSummary.monthlyOptions(last)
        )
    }

    @Test
    fun `the fourth Friday that is also the last is offered both ways`() {
        // 2026-02-27 is the fourth and last Friday of a 28-day February.
        assertEquals(
            listOf(
                MonthlyDay.OfMonth(27),
                MonthlyDay.Nth(4, DayOfWeek.FRIDAY),
                MonthlyDay.Nth(-1, DayOfWeek.FRIDAY)
            ),
            RepeatSummary.monthlyOptions(LocalDate.parse("2026-02-27"))
        )
    }
}
