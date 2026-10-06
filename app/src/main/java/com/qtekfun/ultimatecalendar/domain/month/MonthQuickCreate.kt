// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.month

import java.time.LocalDate
import java.time.LocalDateTime

/** Where a long press on a day of the month starts a new event (RF-03). */
object MonthQuickCreate {
    private const val MORNING_HOUR = 9
    private const val LAST_HOUR = 23

    /**
     * The start for a new event on [date]: 09:00, or, for today, the next full hour after [now]
     * (no later than 23:00, so the event stays on the day).
     */
    fun startFor(date: LocalDate, now: LocalDateTime): LocalDateTime =
        if (date == now.toLocalDate()) {
            date.atTime((now.hour + 1).coerceAtMost(LAST_HOUR), 0)
        } else {
            date.atTime(MORNING_HOUR, 0)
        }
}
