// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.detail

import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.ZoneOffset

/**
 * The occurrence the detail screen was opened for: an event id and when that occurrence is, as
 * plain numbers so the screen can be restored after a rotation. All-day instances are dated in
 * UTC (the source's contract), so their days are the dates of the two instants in UTC.
 */
data class EventRef(
    val eventId: EventId,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean
) {
    /** When the occurrence is, on [master]'s zone; a single event simply keeps its own time. */
    fun timeOn(master: Event): EventTime = when {
        !master.isRecurring -> master.time

        allDay || master.time is EventTime.AllDay -> allDayTime()

        else -> EventTime.Timed(
            Instant.ofEpochMilli(startMillis),
            Instant.ofEpochMilli(endMillis),
            (master.time as EventTime.Timed).zone
        )
    }

    private fun allDayTime(): EventTime {
        val start = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val end = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return EventTime.AllDay(start, if (end.isAfter(start)) end else start.plusDays(1))
    }

    /** The ref as text, to save it with the rest of the navigation state. */
    fun encode(): String =
        "${eventId.value}$SEPARATOR$startMillis$SEPARATOR$endMillis$SEPARATOR$allDay"

    companion object {
        private const val SEPARATOR = ':'

        /** The ref that [encode] wrote, or null for anything else. */
        fun decode(text: String?): EventRef? = runCatching {
            val parts = requireNotNull(text).split(SEPARATOR).iterator()
            val ref = EventRef(
                EventId(parts.next().toLong()),
                parts.next().toLong(),
                parts.next().toLong(),
                parts.next().toBooleanStrict()
            )
            require(!parts.hasNext())
            ref
        }.getOrNull()

        fun of(instance: EventInstance): EventRef = EventRef(
            instance.eventId,
            instance.time.startIn(ZoneOffset.UTC).toEpochMilli(),
            instance.time.endIn(ZoneOffset.UTC).toEpochMilli(),
            instance.time is EventTime.AllDay
        )
    }
}
