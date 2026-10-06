// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.agenda

import java.time.LocalDate
import java.time.YearMonth

/**
 * A row of the agenda list, flat so the list can be a plain lazy column. [date] is the day the
 * row belongs to, [key] identifies it across reloads (so the list keeps its place when days are
 * added above it).
 */
sealed interface AgendaItem {
    val date: LocalDate
    val key: String

    /** The month name above the first day with events of that month. */
    data class MonthDivider(override val date: LocalDate, val month: YearMonth) : AgendaItem {
        override val key: String get() = "m-$month"
    }

    /** The sticky header of a day: its number and weekday. */
    data class DayHeader(override val date: LocalDate) : AgendaItem {
        override val key: String get() = "d-${date.toEpochDay()}"
    }

    /** An event on [date]; [index] is its place in that day. */
    data class EventRow(override val date: LocalDate, val index: Int, val entry: AgendaEntry) :
        AgendaItem {
        override val key: String
            get() = "e-${date.toEpochDay()}-${entry.instance.eventId.value}-$index"
    }
}

/** Turns days into list rows and finds places in them. */
object AgendaItems {
    /** Month divider, day header and event rows, in order; a divider whenever the month changes. */
    fun flatten(days: List<AgendaDay>): List<AgendaItem> {
        var month: YearMonth? = null
        return days.flatMap { day ->
            val current = YearMonth.from(day.date)
            val divider = if (current != month) {
                month = current
                listOf(AgendaItem.MonthDivider(day.date, current))
            } else {
                emptyList()
            }
            divider + AgendaItem.DayHeader(day.date) + day.entries.mapIndexed { index, entry ->
                AgendaItem.EventRow(day.date, index, entry)
            }
        }
    }

    /**
     * Where to scroll to show [date]: its header, or that of the first day after it that has
     * events, with the month divider above when there is one. The last row when nothing is later.
     */
    fun scrollIndex(items: List<AgendaItem>, date: LocalDate): Int {
        val header = items.indexOfFirst { it is AgendaItem.DayHeader && !it.date.isBefore(date) }
        if (header < 0) return (items.size - 1).coerceAtLeast(0)
        return if (items.getOrNull(header - 1) is AgendaItem.MonthDivider) header - 1 else header
    }

    /** The day of the row at [index], null when there is no such row. */
    fun dateAt(items: List<AgendaItem>, index: Int): LocalDate? = items.getOrNull(index)?.date
}
