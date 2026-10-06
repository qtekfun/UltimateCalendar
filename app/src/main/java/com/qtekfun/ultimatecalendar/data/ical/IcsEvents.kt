// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.recurrence.EventSeries
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceOverride
import java.time.Instant
import java.time.ZoneId

/**
 * One calendar object resource as the domain sees it: the [series] (the master event, its
 * `EXDATE`s, `RDATE`s and `RECURRENCE-ID` overrides) with the iCalendar details the domain does
 * not model. A cancelled occurrence is an override without replacement. [status] is the master's.
 */
data class IcsEvent(
    val uid: String,
    val status: EventStatus,
    val sequence: Int,
    val series: EventSeries,
    /** Set only for a file with just an override (an invitation to one occurrence). */
    val masterRecurrenceId: OccurrenceKey? = null
)

/**
 * Maps a `VCALENDAR` to [IcsEvent] and back. All the `VEVENT`s of a resource share one UID: the
 * one without `RECURRENCE-ID` is the master (a file with only overrides, such as an invitation to
 * one occurrence, uses its first `VEVENT`). Every event of the series gets the same [EventId],
 * the one of the resource; the local store decides what that is.
 */
object IcsEvents {
    /** The event of [calendar], or null when it has no readable `VEVENT`. */
    fun read(
        calendar: IcsComponent,
        calendarId: CalendarId,
        eventId: EventId,
        floating: ZoneId
    ): IcsEvent? {
        val all = VeventMapper.read(calendar, floating)
        val master = all.firstOrNull { it.recurrenceId == null } ?: all.firstOrNull() ?: return null
        val overrides = all.filter { it !== master && it.recurrenceId != null }.map {
            OccurrenceOverride(
                recurrenceId = requireNotNull(it.recurrenceId),
                replacement = it.takeIf { o -> o.status != EventStatus.CANCELLED }
                    ?.toEvent(eventId, calendarId)
            )
        }
        return IcsEvent(
            uid = master.uid,
            status = master.status,
            sequence = master.sequence,
            masterRecurrenceId = master.recurrenceId,
            series = EventSeries(
                event = master.toEvent(eventId, calendarId),
                exDates = master.exDates,
                rDates = master.rDates,
                overrides = overrides
            )
        )
    }

    /**
     * [base] with its event updated to [event], or a new calendar when [base] is null (see
     * [VeventMapper.write]). Untouched properties and everything the app does not understand
     * keep their original text. A cancelled occurrence is written as an override with
     * `STATUS:CANCELLED`.
     */
    fun write(base: IcsComponent?, event: IcsEvent, now: Instant, floating: ZoneId): IcsComponent {
        val series = event.series
        val master = VeventFields.of(
            series.event,
            uid = event.uid,
            status = event.status,
            exDates = series.exDates,
            rDates = series.rDates,
            recurrenceId = event.masterRecurrenceId
        )
        // A cancelled occurrence the file already has is kept as it is: the domain only says "gone".
        val cancelledInFile = base?.let { VeventMapper.read(it, floating) }.orEmpty()
            .filter { it.status == EventStatus.CANCELLED && it.recurrenceId != null }
            .associateBy { it.recurrenceId }
        val overrides = series.overrides.map { override ->
            val replacement = override.replacement
            if (replacement != null) {
                VeventFields.of(replacement, event.uid, recurrenceId = override.recurrenceId)
            } else {
                cancelledInFile[override.recurrenceId] ?: VeventFields(
                    uid = event.uid,
                    title = series.event.title,
                    time = VeventMapper.placeholderTime(override.recurrenceId, series.event.time),
                    status = EventStatus.CANCELLED,
                    recurrenceId = override.recurrenceId
                )
            }
        }
        return VeventMapper.write(base, listOf(master) + overrides, now, floating)
    }
}
