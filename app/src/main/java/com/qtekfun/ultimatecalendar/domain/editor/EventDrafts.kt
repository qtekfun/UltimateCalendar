// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft

/** The fields of a stored event as a draft, to write an occurrence or a new series. */
fun Event.toDraft() = EventDraft(
    calendarId = calendarId,
    title = title,
    time = time,
    location = location,
    description = description,
    color = color,
    availability = availability,
    rrule = rrule,
    attendees = attendees,
    reminders = reminders
)
