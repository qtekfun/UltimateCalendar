// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceSplitter
import com.qtekfun.ultimatecalendar.domain.recurrence.SeriesChange
import com.qtekfun.ultimatecalendar.domain.recurrence.SeriesPosition
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** How to take back a move or a change of duration, with the same primitives that made it. */
sealed interface MoveUndo {
    /** Store [event] again as it was (a single event, or a whole series that was updated). */
    data class Restore(val event: Event) : MoveUndo

    /** Store the occurrence that was at [originalStart] again as [draft], its old self. */
    data class RestoreOccurrence(
        val seriesId: EventId,
        val originalStart: Instant,
        val draft: EventDraft
    ) : MoveUndo

    /** Delete the series a split started ([newSeries]) and put [master] back as it was. */
    data class Unsplit(val master: Event, val newSeries: EventId) : MoveUndo
}

/**
 * Applies a drag on the grid (T18) to a [CalendarSource]: changes the time of an event or of
 * the occurrences of a series the user chose, and says how to take it back. The decision for a
 * series is `RecurrenceSplitter.edit`; `SeriesChanges` carries it out (and puts the old series
 * back if starting the new one fails). Only the time changes: nothing else of the event.
 */
class EventMover(private val source: CalendarSource, private val zone: () -> ZoneId) {
    /**
     * Gives [instance] the time [newTime]. For an occurrence of a repeating event, [scope] says
     * which occurrences; it is ignored for an event that does not repeat (also for an occurrence
     * that was stored on its own) and required for one that does.
     */
    suspend fun move(
        instance: EventInstance,
        newTime: EventTime,
        scope: RecurrenceScope?
    ): CalendarResult<MoveUndo> = when (val found = source.event(instance.eventId)) {
        is CalendarResult.Failure -> found

        is CalendarResult.Success -> {
            val master = found.value
            when (val change = plan(master, instance, newTime, scope)) {
                is CalendarResult.Failure -> change

                is CalendarResult.Success ->
                    SeriesChanges
                        .applyTracked(source, master, change.value)
                        .map { created -> undoOf(master, instance, change.value, created) }
            }
        }
    }

    /** Takes back what [move] did. */
    suspend fun undo(undo: MoveUndo): CalendarResult<Unit> = when (undo) {
        is MoveUndo.Restore -> source.update(undo.event)

        is MoveUndo.RestoreOccurrence ->
            source.editInstance(undo.seriesId, undo.originalStart, undo.draft)

        is MoveUndo.Unsplit -> {
            val removed = source.delete(undo.newSeries)
            if (removed is CalendarResult.Failure) removed else source.update(undo.master)
        }
    }

    private fun plan(
        master: Event,
        instance: EventInstance,
        newTime: EventTime,
        scope: RecurrenceScope?
    ): CalendarResult<SeriesChange> = when {
        !master.isRecurring ->
            CalendarResult.Success(SeriesChange.Update(master.copy(time = newTime)))

        scope == null ->
            CalendarResult.Failure(CalendarError.Invalid("a repeating event needs a scope"))

        else -> {
            val occurrence = EventRef.of(instance).timeOn(master)
            RecurrenceSplitter.edit(
                master,
                occurrence,
                master.copy(time = newTime),
                scope,
                SeriesPosition.before(master, occurrence, zone())
            )
        }
    }

    private fun undoOf(
        master: Event,
        instance: EventInstance,
        change: SeriesChange,
        created: EventId?
    ): MoveUndo = when (change) {
        is SeriesChange.ReplaceOccurrence -> MoveUndo.RestoreOccurrence(
            master.id,
            change.originalStart.startIn(ZoneOffset.UTC),
            master.copy(time = EventRef.of(instance).timeOn(master), rrule = null).toDraft()
        )

        is SeriesChange.Split -> MoveUndo.Unsplit(
            master,
            requireNotNull(created) { "a split that moves starts a new series" }
        )

        else -> MoveUndo.Restore(master)
    }
}
