// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

/** What the user may do in a calendar, from least to most (`CALENDAR_ACCESS_LEVEL`). */
enum class CalendarAccess {
    NONE,
    FREE_BUSY,
    READ,
    RESPOND,
    CONTRIBUTE,
    EDIT,
    OWNER;

    /** Invitations can be answered. */
    val canRespond: Boolean get() = this >= RESPOND

    /** Events can be created in the calendar. */
    val canCreate: Boolean get() = this >= CONTRIBUTE

    /** Events can be changed and deleted. */
    val canEdit: Boolean get() = this >= EDIT
}
