// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.detail

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** When an occurrence is, ready to be formatted by the UI. */
sealed interface DetailTime {
    /** Whole days from [first] to [last], both included. */
    data class AllDay(val first: LocalDate, val last: LocalDate) : DetailTime {
        val isSingleDay: Boolean get() = first == last
    }

    /**
     * A timed occurrence in the phone's zone. [end] is null for a moment without length.
     * [other] is the same occurrence in the event's own zone, only when that zone is not the
     * phone's.
     */
    data class Timed(val start: ZonedDateTime, val end: ZonedDateTime?, val other: InOtherZone?) :
        DetailTime {
        /** The event ends on the day it starts. */
        val isSameDay: Boolean get() = end == null || start.toLocalDate() == end.toLocalDate()
    }

    /** The occurrence as people in [zone] see it. */
    data class InOtherZone(val zone: ZoneId, val start: ZonedDateTime, val end: ZonedDateTime?)

    companion object {
        /** [time] as seen from [device], the phone's zone. */
        fun of(time: EventTime, device: ZoneId): DetailTime = when (time) {
            is EventTime.AllDay -> AllDay(time.startDate, time.lastDate)

            is EventTime.Timed -> {
                val hasLength = time.end.isAfter(time.start)
                fun at(zone: ZoneId) = time.start.atZone(zone) to
                    if (hasLength) time.end.atZone(zone) else null
                val (start, end) = at(device)
                val other = if (time.zone == device) {
                    null
                } else {
                    val (otherStart, otherEnd) = at(time.zone)
                    InOtherZone(time.zone, otherStart, otherEnd)
                }
                Timed(start, end, other)
            }
        }
    }
}
