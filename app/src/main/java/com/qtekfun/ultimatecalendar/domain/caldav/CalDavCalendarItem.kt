// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.caldav

import com.qtekfun.ultimatecalendar.domain.model.CalendarId

/**
 * A calendar of the CalDAV account as the account screen lists it. [enabled] is the local choice
 * (RF-12): a calendar switched off is not synced and does not show in the views, and nothing
 * changes on the server.
 */
data class CalDavCalendarItem(
    val id: CalendarId,
    val name: String,
    /** ARGB. */
    val color: Int,
    val writable: Boolean,
    val enabled: Boolean
)
