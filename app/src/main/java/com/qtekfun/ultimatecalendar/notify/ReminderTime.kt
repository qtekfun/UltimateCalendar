// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** The time of the reminders: the injected clock and the phone's current zone, together. */
class ReminderTime @Inject constructor(private val clock: Clock, private val zone: SystemZone) {
    fun now(): Instant = clock.instant()

    fun zone(): ZoneId = zone.current()
}
