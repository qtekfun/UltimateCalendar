// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo

/** Which calendars the editor offers and which one a new event goes to. */
object EditorCalendars {
    /** The calendars that accept new events, in the order given. */
    fun writable(all: List<CalendarInfo>): List<CalendarInfo> = all.filter { it.access.canCreate }

    /**
     * The calendar of a new event among [writable]: the one the user chose in Settings
     * ([preferred]), else the one the repository picks ([automatic]), else the first visible one,
     * else the first. Null when there is no calendar to write to.
     */
    fun initial(
        writable: List<CalendarInfo>,
        preferred: CalendarId?,
        automatic: CalendarId?
    ): CalendarInfo? = listOfNotNull(preferred, automatic).firstNotNullOfOrNull { id ->
        writable.firstOrNull { it.id == id }
    } ?: writable.firstOrNull { it.visible } ?: writable.firstOrNull()
}
