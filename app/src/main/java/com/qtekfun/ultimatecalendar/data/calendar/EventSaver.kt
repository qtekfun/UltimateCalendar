// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.calendar

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.domain.editor.EditTarget
import com.qtekfun.ultimatecalendar.domain.editor.toDraft
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceRules
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceSplitter
import com.qtekfun.ultimatecalendar.domain.recurrence.SeriesChange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * Stores what the event editor produced (RF-05). A new event is one `create`; an edit of a
 * repeating event is decided by [RecurrenceSplitter] and carried out here with the primitives of
 * the [CalendarSource]. Nothing is half done: when the second step of a split fails, the first
 * is undone.
 */
class EventSaver @Inject constructor(private val source: CalendarSource) {
    suspend fun create(draft: EventDraft): CalendarResult<Unit> = source.create(draft).map { }

    /**
     * Saves [draft] over the event in [target]. A repeating event is changed for [scope] only
     * (all of it when null); a single event ignores it.
     */
    suspend fun update(
        target: EditTarget,
        draft: EventDraft,
        scope: RecurrenceScope?
    ): CalendarResult<Unit> {
        val master = target.master
        val edited = draft.toEvent(master.id, master.organizer)
        return if (!master.isRecurring) {
            source.update(edited)
        } else {
            val chosen = scope ?: RecurrenceScope.ALL
            val before = occurrencesBefore(target, chosen)
            when (
                val decided =
                    RecurrenceSplitter.edit(master, target.occurrence, edited, chosen, before)
            ) {
                is CalendarResult.Success -> apply(target, decided.value)
                is CalendarResult.Failure -> decided
            }
        }
    }

    private suspend fun apply(target: EditTarget, change: SeriesChange): CalendarResult<Unit> =
        when (change) {
            is SeriesChange.Update -> source.update(change.event)

            is SeriesChange.ReplaceOccurrence -> source.editInstance(
                target.master.id,
                change.originalStart.startIn(ZoneOffset.UTC),
                change.event.toDraft()
            )

            is SeriesChange.Split -> split(target, change)

            // Editing never deletes or cancels anything: the splitter does not return these.
            is SeriesChange.Delete, is SeriesChange.CancelOccurrence ->
                CalendarResult.Failure(CalendarError.Invalid("not an edit"))
        }

    /** The new series first, then the end of the old one; if that fails, the new one goes. */
    private suspend fun split(
        target: EditTarget,
        change: SeriesChange.Split
    ): CalendarResult<Unit> {
        val created = change.newSeries?.let { source.create(it.toDraft()) }
        return when (created) {
            is CalendarResult.Failure -> created

            else -> source.update(change.truncated).also { ended ->
                val id = created?.getOrNull()
                if (ended is CalendarResult.Failure && id != null) source.delete(id)
            }
        }
    }

    /**
     * How many occurrences come before the one being edited, which a split needs to carry a
     * `COUNT` over to the new series. Read from the source (never expanded here) and only when
     * the rule counts and the edit splits the series.
     */
    private suspend fun occurrencesBefore(target: EditTarget, scope: RecurrenceScope): Int {
        val master = target.master
        val counted = master.rrule?.let { RecurrenceRules.parse(it)?.count } != null
        val first = master.time.startIn(ZoneOffset.UTC)
        val at = target.occurrence.startIn(ZoneOffset.UTC)
        return if (scope == RecurrenceScope.THIS_AND_FOLLOWING && counted && at.isAfter(first)) {
            source.instances(TimeRange(first, at), setOf(master.calendarId)).getOrNull()
                .orEmpty().count {
                    it.eventId == master.id && it.time.startIn(ZoneOffset.UTC).isBefore(at)
                }
        } else {
            0
        }
    }
}
