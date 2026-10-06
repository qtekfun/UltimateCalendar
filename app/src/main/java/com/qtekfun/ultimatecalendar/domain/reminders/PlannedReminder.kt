// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import java.time.Instant
import java.util.Objects

/**
 * A notification to show at [at] (RF-07). [id] tells the alarms apart: an occurrence has one per
 * reminder of its event. [start] is when the occurrence starts (midnight of the phone's zone for
 * an [allDay] one).
 */
data class PlannedReminder(
    val id: Long,
    val eventId: EventId,
    val calendarId: CalendarId,
    val title: String,
    val location: String?,
    val start: Instant,
    val allDay: Boolean,
    val at: Instant
) {
    /** One notification per occurrence, whichever of its reminders showed it. */
    val notificationKey: Int get() = Objects.hash(eventId.value, start.toEpochMilli())
}
