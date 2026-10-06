// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules

/** The units a custom reminder can be counted in, with their length in minutes. */
enum class ReminderUnit(val minutes: Int) {
    MINUTES(1),
    HOURS(MINUTES_PER_HOUR),
    DAYS(MINUTES_PER_DAY),
    WEEKS(DAYS_PER_WEEK * MINUTES_PER_DAY)
}

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private const val DAYS_PER_WEEK = 7

/** What the reminder picker offers and how a typed amount becomes minutes. */
object ReminderInput {
    /**
     * The quick choices: the usual offsets for a timed event, and for an all-day one whole days
     * before (0 is the day itself) since it only rings once a day, at the time in Settings.
     */
    fun choices(allDay: Boolean): List<Int> = if (allDay) {
        listOf(0, MINUTES_PER_DAY, 2 * MINUTES_PER_DAY, DAYS_PER_WEEK * MINUTES_PER_DAY)
    } else {
        SettingsRules.REMINDER_CHOICES
    }

    /** The units for a custom reminder: whole days and weeks for all-day events. */
    fun units(allDay: Boolean): List<ReminderUnit> = if (allDay) {
        listOf(ReminderUnit.DAYS, ReminderUnit.WEEKS)
    } else {
        ReminderUnit.entries
    }

    /** [count] [unit] before the event in minutes; null when negative or past what is allowed. */
    fun minutes(count: Int, unit: ReminderUnit): Int? {
        val total = count.toLong() * unit.minutes
        return total.toInt().takeIf { count >= 0 && total <= SettingsRules.MAX_REMINDER_MINUTES }
    }
}
