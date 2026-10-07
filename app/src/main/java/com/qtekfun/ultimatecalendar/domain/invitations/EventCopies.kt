// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.Event
import java.time.ZoneId

/**
 * Tells the copies of one event apart from other events. An invitation to an event of one account
 * that invites another of the user's accounts ends up, once that account syncs, as a second
 * event in the other account's calendar: the same iCalendar UID and start, but another row.
 */
object EventCopies {
    /**
     * What two copies of an event share: its UID and its start (a moved occurrence of a series has
     * the series' UID but another start). An event without a UID falls back to its title, start
     * and organizer, which is all that is left to go by.
     */
    fun identity(event: Event, zone: ZoneId): String {
        val start = event.time.startIn(zone).toEpochMilli()
        return if (event.uid != null) {
            "uid:${event.uid}@$start"
        } else {
            "event:${event.title}@$start/${event.organizer}"
        }
    }
}
