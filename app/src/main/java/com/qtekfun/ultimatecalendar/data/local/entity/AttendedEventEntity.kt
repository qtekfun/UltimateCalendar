// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.entity

import androidx.room3.Entity

/**
 * An upcoming event the user goes to, as it was at the last check (RF-07, changes and
 * cancellations): the next check tells only what differs from this. Keyed by calendar and event;
 * for a series, the next occurrence. [start] and [end] are epoch milliseconds for a timed event
 * (with its [zone]) and epoch days for an all-day one (no zone). The place is only a hash
 * ([placeHash], empty for none). [title] is kept to word a cancellation. [ownEdit] is set when
 * this app is changing the event, so that the change is not told.
 */
@Entity(tableName = "attended_events", primaryKeys = ["calendarId", "eventId"])
data class AttendedEventEntity(
    val calendarId: Long,
    val eventId: Long,
    val title: String,
    val allDay: Boolean,
    val start: Long,
    val end: Long,
    val zone: String?,
    val placeHash: String,
    val ownEdit: Boolean
)
