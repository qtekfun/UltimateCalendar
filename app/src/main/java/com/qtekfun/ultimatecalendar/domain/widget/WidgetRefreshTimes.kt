// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.widget

import java.time.Instant
import java.time.ZoneId

/** When the widgets must draw themselves again although nothing changed (T38). */
object WidgetRefreshTimes {
    /**
     * The next midnight in [zone] after [now], when "today" moves on. Days that last 23 or 25
     * hours because of a clock change are honoured: it is the start of the next calendar day.
     */
    fun nextMidnight(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
}
