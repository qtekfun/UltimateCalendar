// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Which occurrences of a repeating event an edit or a deletion applies to (RF-05). */
enum class RecurrenceScope { THIS, THIS_AND_FOLLOWING, ALL }

/** What a source must do to carry out an edit or a deletion of a repeating event. */
sealed interface SeriesChange {
    /** Replace the stored series by [event] (same id). */
    data class Update(val event: Event) : SeriesChange

    /** Delete the whole series. */
    data class Delete(val id: EventId) : SeriesChange

    /** Store [event] as an exception that replaces the occurrence that was at [originalStart]. */
    data class ReplaceOccurrence(val originalStart: EventTime, val event: Event) : SeriesChange

    /** Cancel the occurrence that was at [originalStart]; the series is not touched. */
    data class CancelOccurrence(val originalStart: EventTime) : SeriesChange

    /**
     * End the series at [truncated] (which keeps its id) and, unless it was a deletion, start
     * [newSeries] (id [RecurrenceSplitter.UNSAVED]) from the split point.
     */
    data class Split(val truncated: Event, val newSeries: Event?) : SeriesChange
}

/**
 * Edits and deletes "only this / this and following / all" of a repeating event, as pure
 * decisions over the model. Repetitions are read from the provider's `Instances`, so the
 * occurrence being changed is given by its [EventTime] there, and nothing is expanded here.
 */
object RecurrenceSplitter {
    /** The id of an event that is not stored yet. */
    val UNSAVED = EventId(0)

    /**
     * Applies [edited] (the occurrence as the user left it, with the rule the editor chose in
     * `edited.rrule`) to the [scope] of [master], whose [occurrence] is being edited.
     * [occurrencesBefore] is how many occurrences precede [occurrence] in the series; only used
     * to carry a `COUNT` over to the new series.
     */
    fun edit(
        master: Event,
        occurrence: EventTime,
        edited: Event,
        scope: RecurrenceScope,
        occurrencesBefore: Int = 0
    ): CalendarResult<SeriesChange> {
        val rrule = master.rrule ?: return invalid("not a repeating event")
        return when {
            scope == RecurrenceScope.THIS -> CalendarResult.Success(
                SeriesChange.ReplaceOccurrence(occurrence, edited.copy(id = UNSAVED, rrule = null))
            )

            scope == RecurrenceScope.ALL || isFirst(master, occurrence) -> CalendarResult.Success(
                SeriesChange.Update(
                    edited.copy(
                        id = master.id,
                        time = SeriesTimes.rebase(occurrence, edited.time, master.time)
                    )
                )
            )

            else -> split(master, rrule, occurrence, edited, occurrencesBefore)
        }
    }

    /** Deletes the [scope] of [master], whose [occurrence] is being deleted. */
    fun delete(
        master: Event,
        occurrence: EventTime,
        scope: RecurrenceScope
    ): CalendarResult<SeriesChange> {
        val rrule = master.rrule ?: return invalid("not a repeating event")
        return when {
            scope == RecurrenceScope.THIS -> CalendarResult.Success(
                SeriesChange.CancelOccurrence(occurrence)
            )

            scope == RecurrenceScope.ALL || isFirst(master, occurrence) -> CalendarResult.Success(
                SeriesChange.Delete(master.id)
            )

            else -> ruleOf(rrule).flatMap { cut(master, it, occurrence) }
                .map { SeriesChange.Split(it, null) }
        }
    }

    /** [master]'s rule, when the app understands it. */
    private fun ruleOf(rrule: String): CalendarResult<RecurrenceRule> {
        val rule = RecurrenceRules.parse(rrule)
        return if (rule == null) {
            invalid("the repetition rule is not supported")
        } else {
            CalendarResult.Success(rule)
        }
    }

    /**
     * [master] with its [rule] ended just before [occurrence]: `UNTIL` is the day before for
     * all-day events and one second before the start (in UTC) for timed ones, and `COUNT` is
     * dropped because both cannot coexist.
     */
    private fun cut(
        master: Event,
        rule: RecurrenceRule,
        occurrence: EventTime
    ): CalendarResult<Event> = if (startOf(occurrence) < startOf(master.time)) {
        invalid("the occurrence is before the series")
    } else {
        CalendarResult.Success(
            master.copy(
                rrule = RecurrenceRules.format(
                    rule.copy(count = null, until = untilBefore(occurrence))
                )
            )
        )
    }

    private fun split(
        master: Event,
        masterRule: String,
        occurrence: EventTime,
        edited: Event,
        occurrencesBefore: Int
    ): CalendarResult<SeriesChange> = ruleOf(masterRule).flatMap { rule ->
        val count = rule.count
        val keepsRule = edited.rrule == masterRule
        if (keepsRule && count != null && occurrencesBefore !in 1 until count) {
            invalid("the occurrence is not inside the counted series")
        } else {
            cut(master, rule, occurrence).map { truncated ->
                val rrule = if (keepsRule && count != null) {
                    RecurrenceRules.format(rule.copy(count = count - occurrencesBefore))
                } else {
                    edited.rrule
                }
                SeriesChange.Split(truncated, edited.copy(id = UNSAVED, rrule = rrule))
            }
        }
    }

    private fun isFirst(master: Event, occurrence: EventTime) =
        startOf(occurrence) == startOf(master.time)

    private fun startOf(time: EventTime) = time.startIn(ZoneOffset.UTC)

    private fun untilBefore(occurrence: EventTime): Until = when (occurrence) {
        is EventTime.AllDay -> Until.Day(occurrence.startDate.minusDays(1))
        is EventTime.Timed -> Until.Moment(occurrence.start.minusSeconds(1))
    }

    private fun invalid(reason: String) = CalendarResult.Failure(CalendarError.Invalid(reason))
}
