// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import java.time.ZoneId

/**
 * The page as it would look with an event at another time (T18): what the grid draws while the
 * event is being dragged, and while its change is being saved. The other events are laid out
 * again around it, so overlaps show as they will be.
 */
object DragPreview {
    /**
     * [page] with [from] shown at [to]. When [from] is not on the page (the finger took it to the
     * next week) it is added, so the page shows where it would land. [color] is the color [from]
     * was drawn with, for when its calendar has no other event on this page.
     */
    fun apply(
        page: TimeGridPage,
        zone: ZoneId,
        from: EventInstance,
        to: EventTime,
        color: Int? = null
    ): TimeGridPage {
        val moved = from.copy(time = to)
        val others = (page.timed.map { it.instance } + page.allDay.map { it.instance })
            .distinct()
            .filter { it != from && it != moved && !(it.eventId == from.eventId && it.time == to) }
        var colors = calendarColors(page)
        if (color != null && from.color == null) colors = colors + (from.calendarId to color)
        return TimeGridLayout.build(
            DateRange(page.days.first(), page.days.last().plusDays(1)),
            zone,
            others + moved,
            colors
        )
    }

    /** The calendar colors the page was drawn with, to draw the same ones again. */
    private fun calendarColors(page: TimeGridPage): Map<CalendarId, Int> {
        val drawn = page.timed.map { it.instance to it.color } +
            page.allDay.map { it.instance to it.color }
        return drawn
            .filter { (instance, color) -> instance.color == null && color != null }
            .associate { (instance, color) -> instance.calendarId to requireNotNull(color) }
    }
}
