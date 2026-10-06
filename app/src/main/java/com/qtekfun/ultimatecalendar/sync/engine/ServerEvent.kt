// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import com.qtekfun.ultimatecalendar.data.ical.IcsComponent
import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.data.ical.IcsEvents
import com.qtekfun.ultimatecalendar.data.ical.IcsParser
import com.qtekfun.ultimatecalendar.data.ical.VeventMapper
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import java.time.Instant
import java.time.ZoneId

/** An event resource as the server has it: the calendar object, the event and its LAST-MODIFIED. */
data class ServerEvent(val component: IcsComponent, val event: IcsEvent, val modifiedAt: Instant?) {
    companion object {
        /**
         * Reads [ics], null when it has no readable event. The ids are the row's; floating times
         * are read in [floating].
         */
        fun read(ics: String, calendarId: Long, eventId: Long, floating: ZoneId): ServerEvent? {
            val component = IcsParser.parse(ics).firstOrNull() ?: return null
            return IcsEvents.read(component, CalendarId(calendarId), EventId(eventId), floating)
                ?.let { ServerEvent(component, it, modifiedAt(component, floating)) }
        }

        private fun modifiedAt(component: IcsComponent, floating: ZoneId): Instant? =
            VeventMapper.read(component, floating).firstOrNull { it.recurrenceId == null }
                ?.modifiedAt
    }
}
