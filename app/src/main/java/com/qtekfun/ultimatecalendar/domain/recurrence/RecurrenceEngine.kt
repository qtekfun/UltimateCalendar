// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Expands a series into the instances that touch a range (RFC 5545): RRULE, EXDATE, RDATE and
 * RECURRENCE-ID overrides, for the CalDAV source, which has no provider doing it.
 *
 * Timed series repeat at the same wall-clock time in their own zone: an occurrence in a
 * daylight saving gap moves forward by the gap, one in an overlap takes the earlier offset, and
 * its length is the exact length of the first. All-day series are plain dates, placed on the
 * timeline with the `zone` given to [expand] (the phone's). A rule that selects no date matching
 * DTSTART does not produce DTSTART: it is not added on top of the rule.
 */
object RecurrenceEngine {
    /** How many periods (days, weeks, months or years) are tried before giving up. */
    const val MAX_PERIODS = 100_000L

    /** Zones differ by less than this from UTC and from each other, so periods this late are past. */
    private const val SLACK_DAYS = 2L

    fun expand(series: EventSeries, range: TimeRange, zone: ZoneId = ZoneOffset.UTC): Expansion {
        val rule = series.event.rrule?.let(RecurrenceRules::parse)
        val reason = unsupportedReason(series.event, rule)
        return if (reason ==
            null
        ) {
            expandSupported(series, rule, range, zone)
        } else {
            Expansion.Unsupported(reason)
        }
    }

    private fun unsupportedReason(event: Event, rule: RecurrenceRule?): UnsupportedReason? = when {
        event.rrule != null && rule == null -> UnsupportedReason.UNPARSEABLE_RULE
        rule != null && hasOrdinalOutsideMonthOrYear(rule) -> UnsupportedReason.ORDINAL_WEEKDAY
        else -> null
    }

    private fun expandSupported(
        series: EventSeries,
        rule: RecurrenceRule?,
        range: TimeRange,
        zone: ZoneId
    ): Expansion {
        val event = series.event
        val generated = if (rule == null) {
            Generated(listOf(event.time), false)
        } else {
            generate(Shape(event.time), rule, range, zone)
        }
        val extra = series.rDates.mapNotNull { shaped(event.time, it) }
        val instances = SeriesInstances.assemble(
            series,
            (generated.times + extra).distinctBy(EventTime::key),
            range,
            zone
        )
        return if (generated.limitReached) {
            Expansion.LimitReached(instances)
        } else {
            Expansion.Complete(instances)
        }
    }

    private fun hasOrdinalOutsideMonthOrYear(rule: RecurrenceRule) =
        (rule.frequency == Frequency.DAILY || rule.frequency == Frequency.WEEKLY) &&
            rule.byDay.any { it.ordinal != null }

    private class Generated(val times: List<EventTime>, val limitReached: Boolean)

    /** The rule's occurrences up to COUNT, UNTIL or the end of [range], whichever comes first. */
    private fun generate(
        shape: Shape,
        rule: RecurrenceRule,
        range: TimeRange,
        zone: ZoneId
    ): Generated {
        val walk = Walk(shape, rule, range, zone)
        var completed = false
        for (index in 0 until MAX_PERIODS) {
            completed = walk.isPastRange(index) || walk.collect(index)
            if (completed) break
        }
        return Generated(walk.times, !completed)
    }

    /** The walk over the periods of a rule, collecting occurrences into [times]. */
    private class Walk(
        private val shape: Shape,
        private val rule: RecurrenceRule,
        private val range: TimeRange,
        private val zone: ZoneId
    ) {
        val times = mutableListOf<EventTime>()
        private var seen = 0

        /** Whether period [index] starts after the range, so no later period matters. */
        fun isPastRange(index: Long): Boolean {
            val firstDay = PeriodDates.firstDay(rule, shape.startDate, index)
            return firstDay.minusDays(SLACK_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant() >=
                range.end
        }

        /** Adds the occurrences of period [index]; true when the rule or the range is exhausted. */
        fun collect(index: Long): Boolean {
            val dates = PeriodDates.of(rule, shape.startDate, index).filter {
                !it.isBefore(shape.startDate)
            }
            for (date in dates) {
                val time = shape.at(date)
                val finished = pastUntil(rule.until, time, date) ||
                    seen == rule.count ||
                    !time.startIn(zone).isBefore(range.end)
                if (finished) return true
                seen++
                times += time
            }
            return false
        }
    }

    private fun pastUntil(until: Until?, time: EventTime, date: LocalDate): Boolean = when {
        until == null -> false
        time is EventTime.AllDay -> date.isAfter(until.lastDay(ZoneOffset.UTC))
        else -> pastUntil(until, time as EventTime.Timed, date)
    }

    private fun pastUntil(until: Until, time: EventTime.Timed, date: LocalDate): Boolean =
        when (until) {
            is Until.Moment -> time.start.isAfter(until.at)
            is Until.Day -> date.isAfter(until.date)
        }

    /** Builds occurrences with the wall-clock time and length of the series' first one. */
    private class Shape(private val first: EventTime) {
        val startDate: LocalDate = when (first) {
            is EventTime.AllDay -> first.startDate
            is EventTime.Timed -> first.start.atZone(first.zone).toLocalDate()
        }

        fun at(date: LocalDate): EventTime = when (first) {
            is EventTime.AllDay -> EventTime.AllDay(
                date,
                date.plusDays(ChronoUnit.DAYS.between(first.startDate, first.endDate))
            )

            is EventTime.Timed -> if (date == startDate) {
                first
            } else {
                val localTime = first.start.atZone(first.zone).toLocalTime()
                val start = ZonedDateTime.of(date, localTime, first.zone).toInstant()
                EventTime.Timed(
                    start,
                    start.plus(Duration.between(first.start, first.end)),
                    first.zone
                )
            }
        }
    }

    /** The occurrence an RDATE [key] stands for, shaped like [first]; null if of the other kind. */
    private fun shaped(first: EventTime, key: OccurrenceKey): EventTime? = when {
        first is EventTime.AllDay && key is OccurrenceKey.Day -> Shape(first).at(key.date)

        first is EventTime.Timed && key is OccurrenceKey.Moment ->
            EventTime.Timed(
                key.at,
                key.at.plus(Duration.between(first.start, first.end)),
                first.zone
            )

        else -> null
    }
}
