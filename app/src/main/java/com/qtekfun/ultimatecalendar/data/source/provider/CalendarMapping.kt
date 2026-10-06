// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Calendars
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo

/** `Calendars` rows to [CalendarInfo]. Sync columns (`CAL_SYNC*`) are never read. */
internal object CalendarMapping {
    private const val OPAQUE = 0xFF000000.toInt()
    private const val LEVEL_FREE_BUSY = 100
    private const val LEVEL_READ = 200
    private const val LEVEL_RESPOND = 300
    private const val LEVEL_CONTRIBUTE = 500
    private const val LEVEL_EDIT = 600
    private const val LEVEL_OWNER = 700

    val projection = listOf(
        Calendars._ID,
        Calendars.ACCOUNT_NAME,
        Calendars.ACCOUNT_TYPE,
        Calendars.CALENDAR_DISPLAY_NAME,
        Calendars.CALENDAR_COLOR,
        Calendars.CALENDAR_ACCESS_LEVEL,
        Calendars.VISIBLE,
        Calendars.OWNER_ACCOUNT
    )

    /**
     * `CALENDAR_ACCESS_LEVEL` to [CalendarAccess]. Unknown values fall to the level below them,
     * and "override" (400), which can answer invitations but not add events, to [CalendarAccess.RESPOND].
     */
    fun accessOf(level: Int): CalendarAccess = when {
        level >= LEVEL_OWNER -> CalendarAccess.OWNER
        level >= LEVEL_EDIT -> CalendarAccess.EDIT
        level >= LEVEL_CONTRIBUTE -> CalendarAccess.CONTRIBUTE
        level >= LEVEL_RESPOND -> CalendarAccess.RESPOND
        level >= LEVEL_READ -> CalendarAccess.READ
        level >= LEVEL_FREE_BUSY -> CalendarAccess.FREE_BUSY
        else -> CalendarAccess.NONE
    }

    /** Provider colors may lack the alpha byte; the app's colors are ARGB. */
    fun opaque(color: Int): Int = color or OPAQUE

    /** The calendar of [row], or null when it has no id. */
    fun toCalendar(row: ProviderRow): CalendarInfo? {
        val id = row.long(Calendars._ID) ?: return null
        return CalendarInfo(
            id = CalendarId(id),
            account = CalendarAccount(
                name = row.text(Calendars.ACCOUNT_NAME).orEmpty(),
                type = row.text(Calendars.ACCOUNT_TYPE).orEmpty()
            ),
            displayName = row.text(Calendars.CALENDAR_DISPLAY_NAME).orEmpty(),
            color = opaque(row.int(Calendars.CALENDAR_COLOR) ?: 0),
            access = accessOf(row.int(Calendars.CALENDAR_ACCESS_LEVEL) ?: 0),
            visible = row.flag(Calendars.VISIBLE),
            ownerEmail = row.text(Calendars.OWNER_ACCOUNT)?.trim()?.lowercase()?.ifEmpty { null }
        )
    }
}
