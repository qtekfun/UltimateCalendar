// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.widget

import com.qtekfun.ultimatecalendar.domain.agenda.AgendaDay
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaEntry
import com.qtekfun.ultimatecalendar.domain.agenda.AgendaSlot
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import java.time.Instant
import java.time.LocalDate

/** A row of the Agenda widget's list, flat like the app's agenda. */
sealed interface AgendaWidgetRow {
    /** The header of a day with events. */
    data class Day(val date: LocalDate, val isToday: Boolean, val isTomorrow: Boolean) :
        AgendaWidgetRow

    /** An event on the day above; tapping it opens its detail. */
    data class Event(val entry: AgendaEntry) : AgendaWidgetRow {
        val tap: WidgetTap get() = WidgetTap.OpenEvent(EventRef.of(entry.instance))
    }

    /** The events that did not fit; tapping it opens the app. */
    data class More(val count: Int) : AgendaWidgetRow
}

/**
 * Which days and events the Agenda widget lists (T38). The widget scrolls, so it is not cut to
 * its size, only to a sensible total ([MAX_EVENTS]) with a "+N more" row after it. Repetitions
 * are never expanded here: they arrive as instances from the source.
 */
object AgendaWidgetRows {
    const val DAYS_AHEAD = 30L
    const val MAX_EVENTS = 40

    /** The days to read: today and a month ahead. */
    fun range(today: LocalDate) = DateRange(today, today.plusDays(DAYS_AHEAD))

    /**
     * The rows for [days] (already bucketed, all-day events first): nothing before [today], and
     * today's timed events that are over at [now] left out. Days left without events vanish.
     */
    fun build(
        days: List<AgendaDay>,
        today: LocalDate,
        now: Instant,
        maxEvents: Int = MAX_EVENTS
    ): List<AgendaWidgetRow> {
        require(maxEvents > 0) { "At least one event must fit" }
        val kept = days
            .filter { !it.date.isBefore(today) }
            .map { day ->
                if (day.date == today) {
                    day.copy(entries = day.entries.filterNot { isOver(it, now) })
                } else {
                    day
                }
            }
            .filter { it.entries.isNotEmpty() }
        val total = kept.sumOf { it.entries.size }
        var room = maxEvents
        val rows = ArrayList<AgendaWidgetRow>()
        for (day in kept) {
            if (room == 0) break
            rows += AgendaWidgetRow.Day(day.date, day.date == today, day.date == today.plusDays(1))
            day.entries.take(room).forEach { rows += AgendaWidgetRow.Event(it) }
            room -= minOf(room, day.entries.size)
        }
        if (total > maxEvents) rows += AgendaWidgetRow.More(total - maxEvents)
        return rows
    }

    /** A timed entry that has finished at [now]; all-day and open-ended ones never are. */
    private fun isOver(entry: AgendaEntry, now: Instant): Boolean = when (val slot = entry.slot) {
        is AgendaSlot.Span -> !slot.end.isAfter(now)
        is AgendaSlot.Until -> !slot.end.isAfter(now)
        is AgendaSlot.From, AgendaSlot.AllDay -> false
    }
}
