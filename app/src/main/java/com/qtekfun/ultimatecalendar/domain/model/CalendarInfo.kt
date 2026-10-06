// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

/**
 * A calendar. [color] is ARGB. [ownerEmail] identifies "me" for invitations (RF-06). [visible]
 * is the source's own choice; the app may keep a local override.
 */
data class CalendarInfo(
    val id: CalendarId,
    val account: CalendarAccount,
    val displayName: String,
    val color: Int,
    val access: CalendarAccess,
    val visible: Boolean = true,
    val ownerEmail: String? = null
)
