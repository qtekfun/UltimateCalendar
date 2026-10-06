// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

import java.time.Instant

/** A half-open span of time, [start] inclusive and [end] exclusive, to read instances from. */
data class TimeRange(val start: Instant, val end: Instant) {
    init {
        require(end.isAfter(start)) { "A range must last some time" }
    }
}
