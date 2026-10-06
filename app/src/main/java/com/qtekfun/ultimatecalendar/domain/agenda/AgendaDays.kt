// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.agenda

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Buckets the instances of a range into the days of the agenda (RF-03). An instance appears on
 * every day it touches in the device's zone; all-day events come first, the rest by start. Days
 * without events are left out. Repetitions are never expanded here: they arrive already expanded
 * as instances from the source.
 */
object AgendaDays {
    fun build(
        range: DateRange,
        zone: ZoneId,
        instances: List<EventInstance>,
        calendarColors: Map<CalendarId, Int> = emptyMap()
    ): List<AgendaDay> {
        val buckets = HashMap<LocalDate, MutableList<AgendaEntry>>()
        val lastDay = range.endExclusive.minusDays(1)
        instances.forEach { instance ->
            val (first, last) = daysOf(instance.time, zone)
            val color = instance.color ?: calendarColors[instance.calendarId]
            val other = otherZone(instance.time, zone)
            generateSequence(maxOf(first, range.start)) { it.plusDays(1) }
                .takeWhile { !it.isAfter(minOf(last, lastDay)) }
                .forEach { day ->
                    val entry =
                        AgendaEntry(instance, slotOn(day, instance.time, zone), color, other)
                    buckets.getOrPut(day) { mutableListOf() }.add(entry)
                }
        }
        return buckets.toSortedMap().map { (day, entries) ->
            AgendaDay(day, entries.sortedWith(ENTRY_ORDER))
        }
    }

    /** First and last day (inclusive) of an event; a timed one ending at midnight stops the day before. */
    private fun daysOf(time: EventTime, zone: ZoneId): Pair<LocalDate, LocalDate> = when (time) {
        is EventTime.AllDay -> time.startDate to time.lastDate

        is EventTime.Timed -> {
            val first = time.start.atZone(zone).toLocalDate()
            val last = if (time.end > time.start) {
                time.end.minusNanos(1).atZone(zone).toLocalDate()
            } else {
                first
            }
            first to last
        }
    }

    private fun slotOn(day: LocalDate, time: EventTime, zone: ZoneId): AgendaSlot = when (time) {
        is EventTime.AllDay -> AgendaSlot.AllDay

        is EventTime.Timed -> {
            val dayStart = day.atStartOfDay(zone).toInstant()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
            when {
                time.start <= dayStart && time.end >= dayEnd -> AgendaSlot.AllDay
                time.start < dayStart -> AgendaSlot.Until(time.end)
                time.end > dayEnd || time.end == time.start -> AgendaSlot.From(time.start)
                else -> AgendaSlot.Span(time.start, time.end)
            }
        }
    }

    private fun otherZone(time: EventTime, device: ZoneId): ZoneId? =
        (time as? EventTime.Timed)?.let { timed ->
            timed.zone.takeIf {
                it.rules.getOffset(timed.start) != device.rules.getOffset(timed.start)
            }
        }

    private fun startOf(entry: AgendaEntry): Instant = when (val slot = entry.slot) {
        is AgendaSlot.Span -> slot.start
        is AgendaSlot.From -> slot.start
        is AgendaSlot.Until, AgendaSlot.AllDay -> Instant.MIN
    }

    private fun startDateOf(entry: AgendaEntry): LocalDate = when (val time = entry.instance.time) {
        is EventTime.AllDay -> time.startDate
        is EventTime.Timed -> LocalDate.MIN
    }

    private val ENTRY_ORDER: Comparator<AgendaEntry> =
        compareBy<AgendaEntry> { it.slot != AgendaSlot.AllDay }
            .thenBy { startDateOf(it) }
            .thenBy { startOf(it) }
            .thenBy { it.instance.title.lowercase() }
            .thenBy { it.instance.eventId.value }
}
