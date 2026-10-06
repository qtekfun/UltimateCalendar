// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.ZoneOffset

/**
 * What the event detail needs to open this invitation (RF-04): the event and the occurrence the
 * invitation is about. All-day events are dated in UTC, as the source gives its instances.
 */
fun Invitation.detailRef(): EventRef = when (val moment = time) {
    is EventTime.Timed -> EventRef(
        key.eventId,
        moment.start.toEpochMilli(),
        moment.end.toEpochMilli(),
        allDay = false
    )

    is EventTime.AllDay -> EventRef(
        key.eventId,
        moment.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        moment.endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        allDay = true
    )
}
