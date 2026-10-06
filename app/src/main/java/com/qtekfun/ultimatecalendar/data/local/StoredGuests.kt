// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeRole
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
internal data class StoredAttendee(
    val email: String,
    val name: String?,
    val role: AttendeeRole,
    val status: AttendeeStatus,
    val organizer: Boolean
)

@Serializable
internal data class StoredReminder(val minutes: Int, val method: ReminderMethod)

/** The attendees and reminders of an event as the JSON the rows keep them in. */
internal object StoredGuests {
    private val json = Json { ignoreUnknownKeys = true }
    private val attendees = ListSerializer(StoredAttendee.serializer())
    private val reminders = ListSerializer(StoredReminder.serializer())

    fun attendees(text: String): List<Attendee> =
        json.decodeFromString(attendees, text).map(::attendee)

    fun attendeesText(list: List<Attendee>): String =
        json.encodeToString(attendees, list.map(::storedAttendee))

    fun reminders(text: String): List<Reminder> =
        json.decodeFromString(reminders, text).map(::reminder)

    fun remindersText(list: List<Reminder>): String =
        json.encodeToString(reminders, list.map(::storedReminder))

    fun attendee(it: StoredAttendee) = Attendee(it.email, it.name, it.role, it.status, it.organizer)

    fun reminder(it: StoredReminder) = Reminder(it.minutes, it.method)

    fun storedAttendee(it: Attendee) =
        StoredAttendee(it.email, it.name, it.role, it.status, it.isOrganizer)

    fun storedReminder(it: Reminder) = StoredReminder(it.minutesBefore, it.method)
}
