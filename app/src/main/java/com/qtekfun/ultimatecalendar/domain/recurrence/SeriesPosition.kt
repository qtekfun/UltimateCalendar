// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import java.time.ZoneId

/** Where an occurrence is in its series. */
object SeriesPosition {
    /**
     * How many occurrences of [master]'s rule come before [occurrence]: what
     * [RecurrenceSplitter.edit] needs to carry a `COUNT` over to the series that starts there.
     * Counted from the rule itself, so occurrences that were edited or cancelled still count. 0
     * when the rule cannot be expanded (the splitter then refuses a counted series).
     */
    fun before(master: Event, occurrence: EventTime, zone: ZoneId): Int {
        val first = master.time.startIn(zone)
        val limit = occurrence.startIn(zone)
        if (!limit.isAfter(first)) return 0
        val expansion = RecurrenceEngine.expand(EventSeries(master), TimeRange(first, limit), zone)
        val found = when (expansion) {
            is Expansion.Complete -> expansion.instances
            is Expansion.LimitReached -> expansion.instances
            is Expansion.Unsupported -> emptyList()
        }
        return found.count { it.time.startIn(zone).isBefore(limit) }
    }
}
