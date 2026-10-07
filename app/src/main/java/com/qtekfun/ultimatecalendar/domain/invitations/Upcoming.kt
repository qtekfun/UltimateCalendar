// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceRules
import java.time.Clock

/** Whether an event still has an occurrence ahead, judged against [clock] in its zone. */
internal class Upcoming(private val clock: Clock) {
    fun isFuture(event: Event): Boolean {
        val now = clock.instant()
        // A series that began in the past still has future occurrences unless it ended.
        return event.time.startIn(clock.zone).isAfter(now) ||
            (event.rrule != null && !hasEnded(event.rrule))
    }

    private fun hasEnded(rrule: String): Boolean {
        val until = RecurrenceRules.parse(rrule)?.until
        return until != null &&
            until.lastDay(clock.zone).isBefore(clock.instant().atZone(clock.zone).toLocalDate())
    }
}
