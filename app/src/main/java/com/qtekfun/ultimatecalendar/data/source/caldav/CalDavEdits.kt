// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.caldav

import com.qtekfun.ultimatecalendar.data.ical.EventStatus
import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.EventSeries
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceOverride
import com.qtekfun.ultimatecalendar.domain.recurrence.key
import com.qtekfun.ultimatecalendar.sync.conflict.EventField
import java.time.Instant
import java.time.ZoneOffset

/**
 * The local changes of the CalDAV source as pure functions of the stored event: what the event
 * becomes, so that the source can mark the fields that differ and queue the operation.
 */
internal object CalDavEdits {
    /** The fields of [before] that [after] changes. */
    fun changed(before: IcsEvent, after: IcsEvent): Set<EventField> =
        EventField.entries.filter { it.read(before) != it.read(after) }.toSet()

    /**
     * A new event from [draft]. With guests, the user is the organizer ([me], the first address
     * the server knows): a server that schedules (RFC 6638) then sends the invitations when the
     * event is uploaded. Without an address there is no organizer and the guests are only a list.
     */
    fun created(draft: EventDraft, calendar: Long, uid: String, me: List<String>): IcsEvent {
        val organizer = me.firstOrNull()?.takeIf { draft.attendees.isNotEmpty() }
        val event = draft.copy(calendarId = CalendarId(calendar)).toEvent(EventId(0), organizer)
        return IcsEvent(uid, EventStatus.CONFIRMED, 0, EventSeries(event))
    }

    /**
     * [before] with its event replaced by [event]. When the first occurrence moves, the dates of
     * the old exceptions mean nothing any more, so the exceptions and extra dates are dropped; a
     * new rule or length keeps them. A first guest list makes the user the organizer.
     */
    fun replaced(before: IcsEvent, event: Event, me: List<String>): IcsEvent {
        val old = before.series
        val organizer = event.organizer
            ?: me.firstOrNull()?.takeIf { event.attendees.isNotEmpty() }
        val stored = event.copy(
            id = old.event.id,
            calendarId = old.event.calendarId,
            organizer = organizer
        )
        val moved = old.event.time.key() != stored.time.key()
        val series = if (moved) {
            EventSeries(stored)
        } else {
            old.copy(event = stored)
        }
        return before.copy(series = series)
    }

    /**
     * [before] with the user's answer, or null when the user is not an attendee of the series.
     * The answer goes to the master and to every changed occurrence that lists the user.
     */
    fun answered(before: IcsEvent, me: List<String>, status: AttendeeStatus): AnswerResult? {
        val series = before.series
        val mine = series.event.attendees.firstOrNull { it.isOneOf(me) } ?: return null
        fun Event.answer() = copy(
            attendees = attendees.map { if (it.isOneOf(me)) it.copy(status = status) else it }
        )
        val overrides = series.overrides.map {
            OccurrenceOverride(it.recurrenceId, it.replacement?.answer())
        }
        val after = before.copy(
            series = series.copy(event = series.event.answer(), overrides = overrides)
        )
        return AnswerResult(after, mine.email)
    }

    /** The answered event and the address that answered. */
    data class AnswerResult(val event: IcsEvent, val email: String)

    /** The key of the occurrence of [series] that started at [originalStart] (UTC for all day). */
    fun keyOf(series: EventSeries, originalStart: Instant): OccurrenceKey =
        if (series.event.time is EventTime.AllDay) {
            OccurrenceKey.Day(originalStart.atZone(ZoneOffset.UTC).toLocalDate())
        } else {
            OccurrenceKey.Moment(originalStart)
        }

    /**
     * [before] with the occurrence [key] changed to [edit], or cancelled when it is null. A
     * second change of the same occurrence replaces the first.
     */
    fun overridden(before: IcsEvent, key: OccurrenceKey, edit: EventDraft?): IcsEvent {
        val series = before.series
        val master = series.event
        val replacement = edit?.let {
            it.copy(
                rrule = null,
                calendarId = master.calendarId
            ).toEvent(master.id, master.organizer)
        }
        val override = OccurrenceOverride(key, replacement)
        val overrides = series.overrides.filter { it.recurrenceId != key } + override
        return before.copy(series = series.copy(overrides = overrides))
    }

    /** Whether the series is made of several occurrences, so that one can be changed. */
    fun repeats(series: EventSeries): Boolean =
        series.event.rrule != null || series.rDates.isNotEmpty()

    /** The attendees of the occurrence at [time]: its own list if it was changed, else the master's. */
    fun attendeesAt(series: EventSeries, time: EventTime): List<Attendee> =
        series.overrides.firstOrNull { it.replacement?.time == time }?.replacement?.attendees
            ?: series.event.attendees
}
