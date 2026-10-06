// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import java.time.Duration

private const val SHORT = 5L
private const val MEDIUM = 15L
private const val LONG = 60L

/** How long a reminder can be postponed from its notification (RF-07). */
enum class SnoozeOption(val minutes: Long) {
    FIVE_MINUTES(SHORT),
    FIFTEEN_MINUTES(MEDIUM),
    ONE_HOUR(LONG);

    val duration: Duration get() = Duration.ofMinutes(minutes)

    companion object {
        fun fromMinutes(minutes: Long): SnoozeOption? =
            entries.firstOrNull { it.minutes == minutes }
    }
}
