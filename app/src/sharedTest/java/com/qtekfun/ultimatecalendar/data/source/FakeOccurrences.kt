// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.recurrence.Frequency
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceRules
import com.qtekfun.ultimatecalendar.domain.recurrence.Until
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** One occurrence of an event: when it originally starts (UTC) and its time. */
data class Occurrence(val originalStart: Instant, val time: EventTime)

/** Expands the daily and weekly rules with a COUNT or an UNTIL that the contract and the editor use. */
object FakeOccurrences {
    private const val MAX_DAYS = 20_000

    val supported = setOf(Frequency.DAILY, Frequency.WEEKLY)

    /** The occurrences of [event] that start before [before], or all of them when null. */
    fun of(event: Event, before: Instant?): List<Occurrence> {
        val rule = event.rrule?.let { RecurrenceRules.parse(it) }
        return if (rule == null) {
            listOf(Occurrence(event.time.startIn(ZoneOffset.UTC), event.time))
        } else {
            val first = firstDate(event.time)
            val days = rule.byDay.map { it.day }.toSet().ifEmpty { setOf(first.dayOfWeek) }
            generateSequence(first) { it.plusDays(1) }
                .take(MAX_DAYS)
                .filter { matches(rule.frequency, rule.interval, first, it, days) }
                .map { shifted(event.time, it) }
                .takeWhile { before == null || it.startIn(ZoneOffset.UTC).isBefore(before) }
                .takeWhile { rule.until == null || !isAfter(it, rule.until) }
                .take(rule.count ?: Int.MAX_VALUE)
                .map { Occurrence(it.startIn(ZoneOffset.UTC), it) }
                .toList()
        }
    }

    private fun matches(
        frequency: Frequency,
        interval: Int,
        first: LocalDate,
        date: LocalDate,
        days: Set<DayOfWeek>
    ): Boolean = when (frequency) {
        Frequency.DAILY -> ChronoUnit.DAYS.between(first, date) % interval == 0L

        else -> {
            val monday = TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
            val weeks = ChronoUnit.WEEKS.between(first.with(monday), date.with(monday))
            weeks % interval == 0L && date.dayOfWeek in days
        }
    }

    /** Whether [time] starts after the last day or moment [until] allows. */
    private fun isAfter(time: EventTime, until: Until): Boolean = when (until) {
        is Until.Moment -> time.startIn(ZoneOffset.UTC).isAfter(until.at)
        is Until.Day -> firstDate(time).isAfter(until.date)
    }

    private fun firstDate(time: EventTime): LocalDate = when (time) {
        is EventTime.Timed -> time.start.atZone(time.zone).toLocalDate()
        is EventTime.AllDay -> time.startDate
    }

    /** The same event on [date]: same local time of day and same length. */
    private fun shifted(time: EventTime, date: LocalDate): EventTime = when (time) {
        is EventTime.Timed -> {
            val local = time.start.atZone(time.zone).toLocalTime()
            val start = ZonedDateTime.of(date, local, time.zone).toInstant()
            EventTime.Timed(start, start.plus(Duration.between(time.start, time.end)), time.zone)
        }

        is EventTime.AllDay -> EventTime.AllDay(
            date,
            date.plusDays(ChronoUnit.DAYS.between(time.startDate, time.endDate))
        )
    }
}
