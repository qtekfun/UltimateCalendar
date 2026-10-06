// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.month

import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import java.time.LocalDate

/** How an event is drawn in a week row. */
enum class MonthBarStyle {
    /** An all-day event, or one that lasts over midnight: a filled bar across its days. */
    BAR,

    /** A timed event inside one day: a dot and its title. */
    TIMED
}

/**
 * An event over the columns [firstCol]..[lastCol] of one week row, in [lane] (0 is the top).
 * Events that cross a row boundary are cut: [continuesBefore] and [continuesAfter] tell the part
 * is not the whole.
 */
data class MonthBar(
    val instance: EventInstance,
    val firstCol: Int,
    val lastCol: Int,
    val lane: Int,
    val style: MonthBarStyle,
    /** ARGB: the event's own color, else its calendar's; null when neither is known. */
    val color: Int?,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean
) {
    /** An unanswered invitation, drawn as an outline without fill (RF-03, RF-06). */
    val isPending: Boolean get() = instance.selfStatus?.isPending == true

    operator fun contains(col: Int): Boolean = col in firstCol..lastCol
}

/** One row of the month: its seven [days] and the [bars] over them, laid out in lanes. */
data class MonthWeek(val days: List<LocalDate>, val bars: List<MonthBar> = emptyList()) {
    /** The events touching column [col], top lane first. */
    fun barsOn(col: Int): List<MonthBar> = bars.filter { col in it }.sortedBy { it.lane }
}

/** What a Month page draws: its [grid] and one laid-out [MonthWeek] per row. */
data class MonthPage(val grid: MonthGrid, val weeks: List<MonthWeek>)
