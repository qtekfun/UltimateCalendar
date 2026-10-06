// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime

/** Identifies an event across runs. */
data class InvitationKey(val calendarId: CalendarId, val eventId: EventId)

/** A future event in which the user has not answered yet. */
data class Invitation(
    val key: InvitationKey,
    val title: String,
    val time: EventTime,
    val location: String? = null,
    val organizer: String? = null
)
