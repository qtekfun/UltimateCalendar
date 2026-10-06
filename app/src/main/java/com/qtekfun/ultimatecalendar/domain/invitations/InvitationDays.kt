// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.LocalDate
import java.time.ZoneId

/** The invitations that start on one day of the phone's calendar. */
data class InvitationDay(val date: LocalDate, val invitations: List<Invitation>)

/** Orders the invitations of the tray by start and groups them by day (RF-06). */
object InvitationDays {
    /**
     * Soonest first, grouped by the day they start in [zone] (the phone's zone, not the event's).
     * At the same moment an all-day event comes first, then the title decides.
     */
    fun group(invitations: List<Invitation>, zone: ZoneId): List<InvitationDay> {
        val ordered = invitations.sortedWith(
            compareBy<Invitation> { it.time.startIn(zone) }
                .thenBy { it.time !is EventTime.AllDay }
                .thenBy { it.title }
                .thenBy { it.key.calendarId.value }
                .thenBy { it.key.eventId.value }
        )
        return ordered.groupBy { dayOf(it.time, zone) }
            .map { (date, list) -> InvitationDay(date, list) }
    }

    private fun dayOf(time: EventTime, zone: ZoneId): LocalDate = when (time) {
        is EventTime.Timed -> time.start.atZone(zone).toLocalDate()
        is EventTime.AllDay -> time.startDate
    }
}
