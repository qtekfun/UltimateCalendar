// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.data.local.entity.DavEventEntity
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.EventSeries
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceOverride
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** An [OccurrenceKey] as stored: a day (all-day series) or a moment in epoch milliseconds. */
@Serializable
data class StoredKey(val day: String? = null, val at: Long? = null) {
    fun toKey(): OccurrenceKey = day?.let { OccurrenceKey.Day(LocalDate.parse(it)) }
        ?: OccurrenceKey.Moment(Instant.ofEpochMilli(requireNotNull(at)))

    companion object {
        fun of(key: OccurrenceKey): StoredKey = when (key) {
            is OccurrenceKey.Day -> StoredKey(day = key.date.toString())
            is OccurrenceKey.Moment -> StoredKey(at = key.at.toEpochMilli())
        }
    }
}

/** A replacement event of an override: an [Event] without the ids the row decides. */
@Serializable
private data class StoredReplacement(
    val title: String,
    val allDay: Boolean,
    val start: Long,
    val end: Long,
    val zone: String?,
    val location: String?,
    val description: String?,
    val availability: Availability,
    val rrule: String?,
    val organizer: String?,
    val attendees: List<StoredAttendee>,
    val reminders: List<StoredReminder>
)

@Serializable
private data class StoredOverride(val id: StoredKey, val replacement: StoredReplacement?)

/**
 * Maps the event rows of the CalDAV account to the domain and back: the master's columns and the
 * JSON lists become an [IcsEvent] (the series with the iCalendar details the domain does not
 * model), and an [IcsEvent] becomes the columns again. The ids of every event of the series are
 * the row's.
 */
object StoredSeries {
    private val json = Json { ignoreUnknownKeys = true }
    private val keys = ListSerializer(StoredKey.serializer())
    private val overrides = ListSerializer(StoredOverride.serializer())

    /** The event the row holds, with its server-side details (uid, status, sequence). */
    fun read(row: DavEventEntity): IcsEvent = IcsEvent(
        uid = row.uid,
        status = row.status,
        sequence = row.sequence,
        masterRecurrenceId = row.masterRecurrenceId?.let {
            json.decodeFromString(StoredKey.serializer(), it).toKey()
        },
        series = EventSeries(
            event = Event(
                id = EventId(row.id),
                calendarId = CalendarId(row.calendarId),
                title = row.title,
                time = StoredTimes.time(row.allDay, row.start, row.end, row.zone),
                location = row.location,
                description = row.description,
                color = row.color,
                availability = row.availability,
                rrule = row.rrule,
                organizer = row.organizer,
                attendees = StoredGuests.attendees(row.attendees),
                reminders = StoredGuests.reminders(row.reminders)
            ),
            exDates = json.decodeFromString(keys, row.exDates).map { it.toKey() }.toSet(),
            rDates = json.decodeFromString(keys, row.rDates).map { it.toKey() }.toSet(),
            overrides = json.decodeFromString(overrides, row.overrides).map { stored ->
                OccurrenceOverride(
                    stored.id.toKey(),
                    stored.replacement?.let { replacement(it, row) }
                )
            }
        )
    )

    /**
     * [row] holding [event]: every column that comes from the series is replaced, the ones
     * that are about this device and the server copy (ids, href, ETag, ICS, color, dirty
     * flags) stay.
     */
    fun write(row: DavEventEntity, event: IcsEvent): DavEventEntity {
        val master = event.series.event
        val time = StoredTimes.columns(master.time)
        val (windowStart, windowEnd) = StoredTimes.window(event.series)
        return row.copy(
            uid = event.uid,
            title = master.title,
            allDay = master.time is EventTime.AllDay,
            start = time.start,
            end = time.end,
            zone = time.zone,
            windowStart = windowStart,
            windowEnd = windowEnd,
            location = master.location,
            description = master.description,
            availability = master.availability,
            status = event.status,
            sequence = event.sequence,
            rrule = master.rrule,
            organizer = master.organizer,
            attendees = StoredGuests.attendeesText(master.attendees),
            reminders = StoredGuests.remindersText(master.reminders),
            exDates = json.encodeToString(keys, event.series.exDates.map(StoredKey::of)),
            rDates = json.encodeToString(keys, event.series.rDates.map(StoredKey::of)),
            overrides = json.encodeToString(overrides, event.series.overrides.map(::stored)),
            masterRecurrenceId = event.masterRecurrenceId?.let {
                json.encodeToString(StoredKey.serializer(), StoredKey.of(it))
            }
        )
    }

    /** A new row for [event], not on the server yet (no ETag, no ICS). */
    fun create(accountId: Long, calendarId: Long, href: String, event: IcsEvent): DavEventEntity =
        write(
            DavEventEntity(
                accountId = accountId,
                calendarId = calendarId,
                href = href,
                uid = event.uid,
                title = "",
                start = 0,
                end = 0,
                windowStart = 0
            ),
            event
        )

    private fun stored(override: OccurrenceOverride) = StoredOverride(
        StoredKey.of(override.recurrenceId),
        override.replacement?.let { event ->
            val time = StoredTimes.columns(event.time)
            StoredReplacement(
                title = event.title,
                allDay = event.time is EventTime.AllDay,
                start = time.start,
                end = time.end,
                zone = time.zone,
                location = event.location,
                description = event.description,
                availability = event.availability,
                rrule = event.rrule,
                organizer = event.organizer,
                attendees = event.attendees.map(StoredGuests::storedAttendee),
                reminders = event.reminders.map(StoredGuests::storedReminder)
            )
        }
    )

    private fun replacement(stored: StoredReplacement, row: DavEventEntity) = Event(
        id = EventId(row.id),
        calendarId = CalendarId(row.calendarId),
        title = stored.title,
        time = StoredTimes.time(stored.allDay, stored.start, stored.end, stored.zone),
        location = stored.location,
        description = stored.description,
        availability = stored.availability,
        rrule = stored.rrule,
        organizer = stored.organizer,
        attendees = stored.attendees.map(StoredGuests::attendee),
        reminders = stored.reminders.map(StoredGuests::reminder)
    )
}
