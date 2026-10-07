// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Events
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.Reminder

/**
 * `Events` rows to [Event] and back. Sync columns (`_SYNC_ID`, `SYNC_DATA*`…) and other apps'
 * extended properties are never read or written.
 */
internal object EventMapping {
    val projection = listOf(
        Events._ID,
        Events.CALENDAR_ID,
        Events.TITLE,
        Events.DESCRIPTION,
        Events.EVENT_LOCATION,
        Events.EVENT_COLOR,
        Events.DTSTART,
        Events.DTEND,
        Events.DURATION,
        Events.EVENT_TIMEZONE,
        Events.ALL_DAY,
        Events.AVAILABILITY,
        Events.RRULE,
        Events.RDATE,
        Events.ORGANIZER,
        Events.UID_2445,
        Events.DELETED
    )

    fun availabilityOf(code: Int?): Availability = when (code) {
        Events.AVAILABILITY_FREE -> Availability.FREE
        Events.AVAILABILITY_TENTATIVE -> Availability.TENTATIVE
        else -> Availability.BUSY
    }

    fun availabilityCode(availability: Availability): Int = when (availability) {
        Availability.BUSY -> Events.AVAILABILITY_BUSY
        Availability.FREE -> Events.AVAILABILITY_FREE
        Availability.TENTATIVE -> Events.AVAILABILITY_TENTATIVE
    }

    /** Whether the row repeats: it has a rule or extra dates. */
    fun repeats(row: ProviderRow): Boolean =
        !row.text(Events.RRULE).isNullOrBlank() || !row.text(Events.RDATE).isNullOrBlank()

    /** The calendar of [row], or null when it is not an event that can be shown. */
    fun calendarOf(row: ProviderRow): CalendarId? = row.long(Events.CALENDAR_ID)?.let(::CalendarId)

    /**
     * The event of [row] with its [attendees] and [reminders]; null for a row without id or start,
     * or one the user deleted that the provider still keeps for its sync adapter.
     */
    fun toEvent(row: ProviderRow, attendees: List<Attendee>, reminders: List<Reminder>): Event? {
        val id = row.long(Events._ID)
        val start = row.long(Events.DTSTART)
        val calendar = calendarOf(row)
        return if (id == null || start == null || calendar == null) {
            null
        } else if (row.flag(Events.DELETED)) {
            null
        } else {
            Event(
                id = EventId(id),
                calendarId = calendar,
                title = row.text(Events.TITLE).orEmpty(),
                time = EventTimeMapping.read(
                    allDay = row.flag(Events.ALL_DAY),
                    startMs = start,
                    endMs = row.long(Events.DTEND),
                    duration = row.text(Events.DURATION),
                    timeZone = row.text(Events.EVENT_TIMEZONE)
                ),
                location = row.text(Events.EVENT_LOCATION)?.ifEmpty { null },
                description = row.text(Events.DESCRIPTION)?.ifEmpty { null },
                color = row.int(Events.EVENT_COLOR)?.let { CalendarMapping.opaque(it) },
                availability = availabilityOf(row.int(Events.AVAILABILITY)),
                rrule = row.text(Events.RRULE)?.ifBlank { null },
                organizer = row.text(Events.ORGANIZER)?.ifBlank { null },
                attendees = attendees,
                reminders = reminders,
                uid = row.text(Events.UID_2445)?.trim()?.ifEmpty { null }
            )
        }
    }

    /** The fields of an [Event] as a draft, to write them with [toValues]. */
    fun draftOf(event: Event) = EventDraft(
        calendarId = event.calendarId,
        title = event.title,
        time = event.time,
        location = event.location,
        description = event.description,
        color = event.color,
        availability = event.availability,
        rrule = event.rrule,
        attendees = event.attendees,
        reminders = event.reminders
    )

    /**
     * The columns of a new exception of a series. The provider copies the calendar and the rule
     * from the series and refuses ("Exceptions can't overwrite ...") a `CALENDAR_ID`, a `DTEND`
     * or a rule: the length of the occurrence goes in `DURATION`.
     */
    fun toExceptionValues(draft: EventDraft): ProviderRow =
        toValues(draft, repeats = true) - Events.RRULE - Events.DTEND

    /**
     * The `Events` columns that store [draft] (everything but the calendar and the organizer).
     * Absent optional fields are written as null so an update clears them.
     */
    fun toValues(draft: EventDraft, repeats: Boolean = draft.rrule != null): ProviderRow {
        val values = mutableMapOf<String, Any?>(
            Events.TITLE to draft.title,
            Events.EVENT_LOCATION to draft.location,
            Events.DESCRIPTION to draft.description,
            Events.EVENT_COLOR to draft.color,
            Events.AVAILABILITY to availabilityCode(draft.availability),
            Events.RRULE to draft.rrule,
            Events.HAS_ALARM to if (draft.reminders.isEmpty()) 0 else 1,
            Events.HAS_ATTENDEE_DATA to if (draft.attendees.isEmpty()) 0 else 1
        )
        values += EventTimeMapping.write(draft.time, repeats)
        return values
    }
}
