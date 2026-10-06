// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.data.local.entity.DavEventEntity
import com.qtekfun.ultimatecalendar.data.local.entity.SubscriptionEventEntity

/**
 * Subscription event rows and [IcsEvent]s, through [StoredSeries]: the rows have the same columns
 * for the series as a CalDAV event, so the same JSON and the same window rules apply and the
 * `RecurrenceEngine` expands both alike. The ids of the event are the row's and the calendar's
 * the subscription's, as in `StoredSeries`.
 */
internal object StoredSubscriptionEvents {
    fun read(row: SubscriptionEventEntity): IcsEvent = StoredSeries.read(
        DavEventEntity(
            id = row.id,
            accountId = 0,
            calendarId = row.subscriptionId,
            href = "",
            uid = row.uid,
            title = row.title,
            allDay = row.allDay,
            start = row.start,
            end = row.end,
            zone = row.zone,
            windowStart = row.windowStart,
            windowEnd = row.windowEnd,
            location = row.location,
            description = row.description,
            availability = row.availability,
            status = row.status,
            sequence = row.sequence,
            rrule = row.rrule,
            organizer = row.organizer,
            exDates = row.exDates,
            rDates = row.rDates,
            overrides = row.overrides,
            masterRecurrenceId = row.masterRecurrenceId
        )
    )

    /** The row [id] (0 for a new one) of [subscriptionId] holding [event]. */
    fun write(subscriptionId: Long, id: Long, event: IcsEvent): SubscriptionEventEntity {
        val blank = DavEventEntity(
            id = id,
            accountId = 0,
            calendarId = subscriptionId,
            href = "",
            uid = event.uid,
            title = "",
            start = 0,
            end = 0,
            windowStart = 0
        )
        val dav = StoredSeries.write(blank, event)
        return SubscriptionEventEntity(
            id = id,
            subscriptionId = subscriptionId,
            uid = dav.uid,
            title = dav.title,
            allDay = dav.allDay,
            start = dav.start,
            end = dav.end,
            zone = dav.zone,
            windowStart = dav.windowStart,
            windowEnd = dav.windowEnd,
            location = dav.location,
            description = dav.description,
            availability = dav.availability,
            status = dav.status,
            sequence = dav.sequence,
            rrule = dav.rrule,
            organizer = dav.organizer,
            exDates = dav.exDates,
            rDates = dav.rDates,
            overrides = dav.overrides,
            masterRecurrenceId = dav.masterRecurrenceId
        )
    }
}
