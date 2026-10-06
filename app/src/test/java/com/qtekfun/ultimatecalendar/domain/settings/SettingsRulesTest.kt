// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.settings

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsRulesTest {
    @Test
    fun `a duration is kept between five minutes and a day`() {
        assertEquals(5, SettingsRules.duration(-3))
        assertEquals(45, SettingsRules.duration(45))
        assertEquals(24 * 60, SettingsRules.duration(10_000))
    }

    @Test
    fun `only the offered missed windows exist`() {
        SettingsRules.MISSED_WINDOW_CHOICES.forEach {
            assertEquals(it, SettingsRules.missedWindow(it))
        }
        assertEquals(24, SettingsRules.missedWindow(7))
        assertEquals(24, SettingsRules.missedWindow(-1))
    }

    @Test
    fun `the all-day reminder time stays inside the day`() {
        assertEquals(0, SettingsRules.allDayMinute(-5))
        assertEquals(23 * 60 + 59, SettingsRules.allDayMinute(5_000))
        assertEquals(LocalTime.of(9, 30), SettingsRules.allDayTime(9 * 60 + 30))
        assertEquals(LocalTime.MIDNIGHT, SettingsRules.allDayTime(-1))
        assertEquals(48, SettingsRules.ALL_DAY_TIME_CHOICES.size)
    }

    @Test
    fun `reminders are sorted, unique, not negative, not absurd and at most five`() {
        assertEquals(listOf(0, 10, 60), SettingsRules.reminders(listOf(60, 10, 10, -5, 0)))
        assertEquals(
            emptyList<Int>(),
            SettingsRules.reminders(
                listOf(
                    -1,
                    SettingsRules.MAX_REMINDER_MINUTES + 1
                )
            )
        )
        assertEquals(
            listOf(1, 2, 3, 4, 5),
            SettingsRules.reminders(listOf(9, 8, 7, 6, 5, 4, 3, 2, 1))
        )
        assertEquals(
            listOf(SettingsRules.MAX_REMINDER_MINUTES),
            SettingsRules.reminders(listOf(SettingsRules.MAX_REMINDER_MINUTES))
        )
    }

    @Test
    fun `an alias is trimmed and lower cased`() {
        assertEquals("ana@example.com", SettingsRules.alias("  Ana@Example.COM "))
    }

    @Test
    fun `things that are not an address are refused`() {
        listOf(
            "",
            "ana",
            "ana@",
            "@example.com",
            "ana@example",
            "a b@example.com",
            "a@b@example.com",
            "a,b@x.org"
        )
            .forEach { assertNull(SettingsRules.alias(it), it) }
    }

    @Test
    fun `aliases are unique after normalizing, and limited`() {
        assertEquals(
            listOf("a@x.org", "b@x.org"),
            SettingsRules.aliases(listOf("A@x.org", "nonsense", "a@x.org", "b@x.org"))
        )
        val many = (1..30).map { "user$it@x.org" }
        assertEquals(many.take(SettingsRules.MAX_ALIASES), SettingsRules.aliases(many))
    }

    @Test
    fun `a passphrase needs eight characters`() {
        assertFalse(SettingsRules.isPassphraseAcceptable("1234567".toCharArray()))
        assertTrue(SettingsRules.isPassphraseAcceptable("12345678".toCharArray()))
    }

    @Test
    fun `the first day of the week is the locale's unless picked`() {
        assertEquals(DayOfWeek.SUNDAY, FirstDayOfWeek.LOCALE.resolve(Locale.US))
        assertEquals(DayOfWeek.MONDAY, FirstDayOfWeek.LOCALE.resolve(Locale.FRANCE))
        assertEquals(DayOfWeek.MONDAY, FirstDayOfWeek.MONDAY.resolve(Locale.US))
        assertEquals(DayOfWeek.SATURDAY, FirstDayOfWeek.SATURDAY.resolve(Locale.FRANCE))
        assertEquals(DayOfWeek.SUNDAY, FirstDayOfWeek.SUNDAY.resolve(Locale.FRANCE))
    }

    @Test
    fun `the invitation check intervals are 15, 30, 60 minutes or only by hand`() {
        assertEquals(
            listOf(15, 30, 60, null),
            InviteCheckInterval.entries.map { it.minutes }
        )
    }

    @Test
    fun `an offset is said in the coarsest exact unit`() {
        assertEquals(ReminderOffset(OffsetUnit.AT_START, 0), ReminderOffset.of(0))
        assertEquals(ReminderOffset(OffsetUnit.AT_START, 0), ReminderOffset.of(-4))
        assertEquals(ReminderOffset(OffsetUnit.MINUTES, 10), ReminderOffset.of(10))
        assertEquals(ReminderOffset(OffsetUnit.MINUTES, 90), ReminderOffset.of(90))
        assertEquals(ReminderOffset(OffsetUnit.HOURS, 2), ReminderOffset.of(120))
        assertEquals(ReminderOffset(OffsetUnit.DAYS, 1), ReminderOffset.of(24 * 60))
        assertEquals(ReminderOffset(OffsetUnit.DAYS, 2), ReminderOffset.of(2 * 24 * 60))
        assertEquals(ReminderOffset(OffsetUnit.WEEKS, 1), ReminderOffset.of(7 * 24 * 60))
    }
}
