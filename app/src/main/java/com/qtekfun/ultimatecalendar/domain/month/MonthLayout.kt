// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.month

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Turns the instances of a month grid into lanes per week row (RF-03). The source has already
 * expanded repetitions; nothing is expanded here. Events are cut at row boundaries; all-day and
 * multi-day events get their lanes first (longer first), then timed events by start, each below
 * the bars of its day, so an all-day event is always above the timed ones of its day.
 */
object MonthLayout {
    /** An event over whole days of the grid, before it is cut into rows. */
    private class Span(
        val instance: EventInstance,
        val first: LocalDate,
        val last: LocalDate,
        val start: Instant,
        val color: Int?
    ) {
        val style = if (instance.time is EventTime.AllDay || last != first) {
            MonthBarStyle.BAR
        } else {
            MonthBarStyle.TIMED
        }
        val days = ChronoUnit.DAYS.between(first, last)
    }

    fun build(
        grid: MonthGrid,
        zone: ZoneId,
        instances: List<EventInstance>,
        calendarColors: Map<CalendarId, Int> = emptyMap()
    ): MonthPage {
        val spans = instances.map { span(it, zone, calendarColors) }
        return MonthPage(grid, grid.weeks.map { week(it, spans) })
    }

    private fun span(
        instance: EventInstance,
        zone: ZoneId,
        calendarColors: Map<CalendarId, Int>
    ): Span {
        val color = instance.color ?: calendarColors[instance.calendarId]
        return when (val time = instance.time) {
            is EventTime.AllDay -> Span(
                instance,
                time.startDate,
                time.lastDate,
                time.startIn(zone),
                color
            )

            is EventTime.Timed -> {
                val first = time.start.atZone(zone).toLocalDate()
                // An event that ends at midnight sharp does not touch the day that starts there.
                val last = if (time.end > time.start) {
                    time.end.minusNanos(1).atZone(zone).toLocalDate()
                } else {
                    first
                }
                Span(instance, first, last, time.start, color)
            }
        }
    }

    private fun week(days: List<LocalDate>, spans: List<Span>): MonthWeek {
        val weekStart = days.first()
        val weekEnd = days.last()
        val ordered = spans
            .filter { !it.first.isAfter(weekEnd) && !it.last.isBefore(weekStart) }
            .sortedWith(
                compareBy<Span> { it.style }
                    .thenBy { maxOf(it.first, weekStart) }
                    .thenByDescending { it.days }
                    .thenBy { it.start }
            )
        // Per column, the lanes already taken.
        val taken = List(days.size) { sortedSetOf<Int>() }
        val bars = ordered.map { span ->
            val firstCol = ChronoUnit.DAYS.between(weekStart, maxOf(span.first, weekStart)).toInt()
            val lastCol = ChronoUnit.DAYS.between(weekStart, minOf(span.last, weekEnd)).toInt()
            val columns = firstCol..lastCol
            val lane = if (span.style == MonthBarStyle.BAR) {
                generateSequence(0) { it + 1 }.first { free -> columns.none { free in taken[it] } }
            } else {
                taken[firstCol].lastOrNull()?.plus(1) ?: 0
            }
            columns.forEach { taken[it].add(lane) }
            MonthBar(
                instance = span.instance,
                firstCol = firstCol,
                lastCol = lastCol,
                lane = lane,
                style = span.style,
                color = span.color,
                continuesBefore = span.first.isBefore(weekStart),
                continuesAfter = span.last.isAfter(weekEnd)
            )
        }
        return MonthWeek(days, bars)
    }
}
