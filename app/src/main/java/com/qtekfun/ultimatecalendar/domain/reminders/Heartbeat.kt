// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import java.time.Duration
import java.time.Instant

/**
 * A silent alarm that sets every reminder again and brings back missed ones (RF-08), for phones
 * that freeze the app and lose its alarms. It only runs while some reminder is still to come.
 */
object Heartbeat {
    private const val MINUTES = 30L
    val INTERVAL: Duration = Duration.ofMinutes(MINUTES)

    /** When to beat next, or null when no reminder is left to protect. */
    fun next(
        now: Instant,
        pending: List<PlannedReminder>,
        interval: Duration = INTERVAL
    ): Instant? = if (pending.any { it.at.isAfter(now) }) now.plus(interval) else null
}
