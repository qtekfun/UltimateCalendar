// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Turns the wall-clock time a grid shows into a moment (T18). The grid has 24 hours on every day,
 * so on the days of a clock change some of its positions are not one moment:
 * - A time in the hour that is skipped (it never happens) moves forward by the length of the gap.
 * - A time in the hour that happens twice is its first pass (the earlier offset).
 *
 * The answer is always one moment, never a time that does not exist and never an ambiguous one.
 */
object WallClock {
    fun resolve(time: LocalDateTime, zone: ZoneId): Instant =
        ZonedDateTime.ofLocal(time, zone, null).toInstant()
}
