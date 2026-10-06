// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.recurrence.SeriesChange
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.ZoneOffset

/** An [Event] as a draft, to store it again (as a new event or as an edited occurrence). */
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

/**
 * Carries out what `RecurrenceSplitter` decided for a repeating event, with the primitives of a
 * [CalendarSource]. The splitter's occurrence times are the ones of the source's instances, so
 * an occurrence is found by its start in UTC (all-day instances are dated in UTC).
 */
object SeriesChanges {
    /**
     * Applies [change] to [master], the series as it was stored. When a split fails halfway, the
     * series is put back as it was, so a failure leaves the calendar unchanged as far as the
     * source allows.
     */
    suspend fun apply(
        source: CalendarSource,
        master: Event,
        change: SeriesChange
    ): CalendarResult<Unit> = applyTracked(source, master, change).map { }

    /**
     * Like [apply], and answers the id of the series a split started (null when it did not start
     * one), so that a caller can take the split back.
     */
    suspend fun applyTracked(
        source: CalendarSource,
        master: Event,
        change: SeriesChange
    ): CalendarResult<EventId?> = when (change) {
        is SeriesChange.Update -> source.update(change.event).map { null }

        is SeriesChange.Delete -> source.delete(change.id).map { null }

        is SeriesChange.CancelOccurrence ->
            source.cancelInstance(master.id, change.originalStart.startIn(ZoneOffset.UTC))
                .map { null }

        is SeriesChange.ReplaceOccurrence -> source.editInstance(
            master.id,
            change.originalStart.startIn(ZoneOffset.UTC),
            change.event.toDraft()
        ).map { null }

        is SeriesChange.Split -> split(source, master, change)
    }

    private suspend fun split(
        source: CalendarSource,
        master: Event,
        change: SeriesChange.Split
    ): CalendarResult<EventId?> {
        val cut = source.update(change.truncated)
        val next = change.newSeries
        return if (cut is CalendarResult.Failure) {
            cut
        } else if (next == null) {
            CalendarResult.Success(null)
        } else {
            startNext(source, master, next)
        }
    }

    /** Starts [next] after the old series was cut; if that fails, the old series is restored. */
    private suspend fun startNext(
        source: CalendarSource,
        master: Event,
        next: Event
    ): CalendarResult<EventId?> {
        val created = source.create(next.toDraft())
        if (created is CalendarResult.Failure) source.update(master)
        return created
    }
}
