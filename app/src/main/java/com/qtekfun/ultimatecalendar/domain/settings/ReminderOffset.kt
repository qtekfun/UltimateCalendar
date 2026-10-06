// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.settings

/** The unit a reminder offset is best said in. */
enum class OffsetUnit { AT_START, MINUTES, HOURS, DAYS, WEEKS }

/** A reminder offset as "[count] [unit] before" (or at the start), ready to be put in words. */
data class ReminderOffset(val unit: OffsetUnit, val count: Int) {
    companion object {
        private const val HOUR = 60
        private const val DAY = 24 * HOUR
        private const val WEEK = 7 * DAY

        /** The coarsest unit that says [minutes] exactly: 120 is 2 hours, 90 is 90 minutes. */
        fun of(minutes: Int): ReminderOffset = when {
            minutes <= 0 -> ReminderOffset(OffsetUnit.AT_START, 0)
            minutes % WEEK == 0 -> ReminderOffset(OffsetUnit.WEEKS, minutes / WEEK)
            minutes % DAY == 0 -> ReminderOffset(OffsetUnit.DAYS, minutes / DAY)
            minutes % HOUR == 0 -> ReminderOffset(OffsetUnit.HOURS, minutes / HOUR)
            else -> ReminderOffset(OffsetUnit.MINUTES, minutes)
        }
    }
}
