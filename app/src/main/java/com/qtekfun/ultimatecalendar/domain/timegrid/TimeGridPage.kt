// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import java.time.LocalDate
import java.time.ZoneId

/**
 * A timed event, or the part of it on one day, ready to draw in a day grid. [startMinute] and
 * [endMinute] are minutes of the day on the wall clock of the device's zone (0 to 1440). It
 * sits in [column] of [columns] and widens over [span] columns.
 */
data class TimedBlock(
    val instance: EventInstance,
    /** Index of the day within [TimeGridPage.days]. */
    val dayIndex: Int,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columns: Int,
    val span: Int,
    /** ARGB: the event's own color, else its calendar's; null when neither is known. */
    val color: Int?,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
    /** The event's own zone when its wall-clock time there differs from the device's. */
    val otherZone: ZoneId?
) {
    /** An unanswered invitation, drawn as an outline without fill (RF-03, RF-06). */
    val isPending: Boolean get() = instance.selfStatus?.isPending == true
}

/** An all-day (or multi-day) event in the strip above the grid, over days [firstDay]..[lastDay]. */
data class AllDayBar(
    val instance: EventInstance,
    val firstDay: Int,
    val lastDay: Int,
    /** Row of the strip. */
    val row: Int,
    val color: Int?,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean
) {
    val isPending: Boolean get() = instance.selfStatus?.isPending == true
}

/** What a Day, 3 days or Week page draws: its [days], the all-day strip and the timed blocks. */
data class TimeGridPage(
    val days: List<LocalDate>,
    val allDay: List<AllDayBar> = emptyList(),
    val timed: List<TimedBlock> = emptyList()
) {
    /** How many rows the all-day strip needs. */
    val allDayRows: Int get() = (allDay.maxOfOrNull { it.row } ?: -1) + 1
}
