// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope

/** The stored series being edited and the occurrence the user opened. */
data class EditTarget(val master: Event, val occurrence: EventTime)

/** Which parts of a repeating event an edit may apply to (RF-05). */
object EditScopes {
    /**
     * Every scope, minus "only this event" in a local calendar: the provider drops all the
     * occurrences of a client-made series that has no sync id as soon as one of them is changed
     * (found in T05), and local calendars never get one. "This and following" ends the series
     * and starts another, which works everywhere.
     */
    fun available(calendar: CalendarInfo?): List<RecurrenceScope> =
        if (calendar?.account?.isLocal == true) {
            listOf(RecurrenceScope.THIS_AND_FOLLOWING, RecurrenceScope.ALL)
        } else {
            RecurrenceScope.entries
        }

    /** The user is asked only when the event being edited repeats. */
    fun needsChoice(target: EditTarget?): Boolean = target?.master?.isRecurring == true
}
