// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.navigation

import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import java.time.LocalDate
import java.time.ZoneId

/** The days a view shows: [start] inclusive, [endExclusive] exclusive. */
data class DateRange(val start: LocalDate, val endExclusive: LocalDate) {
    init {
        require(endExclusive.isAfter(start)) { "A range must cover some days" }
    }

    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && date.isBefore(endExclusive)

    /**
     * The same days as instants in [zone], to ask a source for instances. Days that last 23 or
     * 25 hours because of a clock change are honoured.
     */
    fun toTimeRange(zone: ZoneId): TimeRange = TimeRange(
        start.atStartOfDay(zone).toInstant(),
        endExclusive.atStartOfDay(zone).toInstant()
    )
}
