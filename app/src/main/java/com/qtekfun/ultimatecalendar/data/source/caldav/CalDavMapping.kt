// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.caldav

import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavCalendarEntity
import com.qtekfun.ultimatecalendar.data.source.CalDavIds
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Rows of the CalDAV account as the domain sees them, with the ids of [CalDavIds]. */
internal object CalDavMapping {
    private const val OPAQUE = 0xFF000000.toInt()
    private const val RGB = 0xFFFFFF
    private const val HEX = 16
    private const val DEFAULT_COLOR = 0xFF0B63CE.toInt()
    private const val COLOR_LENGTH = 7

    /** The account type of every CalDAV calendar: it is not an Android account. */
    const val ACCOUNT_TYPE = CalendarAccount.CALDAV_TYPE

    /** "ana@cloud.example.com": the login and the server's host. */
    fun accountName(account: DavAccountEntity): String {
        val host = account.serverUrl.toHttpUrlOrNull()?.host ?: account.serverUrl
        return "${account.loginName}@$host"
    }

    /**
     * The user's own addresses, as discovery found them. A server that does not tell (no
     * scheduling) is given the login name, when that is an address, so that answers still work.
     */
    fun addresses(account: DavAccountEntity): List<String> =
        account.userAddresses.split(',').filter { it.isNotBlank() }.ifEmpty {
            listOfNotNull(Attendee.normalize(account.loginName).takeIf { '@' in it })
        }

    /** A calendar the user can write to is theirs; a shared read-only one is only readable. */
    fun calendar(row: DavCalendarEntity, account: DavAccountEntity) = CalendarInfo(
        id = CalDavIds.calendar(row.id),
        account = CalendarAccount(accountName(account), ACCOUNT_TYPE),
        displayName = row.name,
        color = color(row.color),
        access = if (row.writable) CalendarAccess.OWNER else CalendarAccess.READ,
        visible = true,
        ownerEmail = addresses(account).firstOrNull()
    )

    /** `#RRGGBB` as opaque ARGB; a server without a (readable) color gets a neutral blue. */
    fun color(text: String?): Int {
        val rgb = text?.takeIf { it.length == COLOR_LENGTH && it.startsWith('#') }
            ?.substring(1)?.toIntOrNull(HEX)
        return rgb?.let { OPAQUE or (it and RGB) } ?: DEFAULT_COLOR
    }

    fun event(event: Event) = event.copy(
        id = CalDavIds.event(event.id.value),
        calendarId = CalDavIds.calendar(event.calendarId.value)
    )

    fun instance(instance: EventInstance) = instance.copy(
        eventId = CalDavIds.event(instance.eventId.value),
        calendarId = CalDavIds.calendar(instance.calendarId.value)
    )

    fun searchable(found: SearchableEvent) = found.copy(
        eventId = CalDavIds.event(found.eventId.value),
        calendarId = CalDavIds.calendar(found.calendarId.value)
    )
}
