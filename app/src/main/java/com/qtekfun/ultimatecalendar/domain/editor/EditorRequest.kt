// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * What opens the editor: a new event (at a tapped time, or at the next half hour) or an
 * existing one, optionally at one occurrence of its series. A request is saved with the
 * navigation state, so it is written to and read from a list of plain strings.
 */
sealed interface EditorRequest {
    /** A new event; [at] is the wall-clock time of the slot the user tapped, if any. */
    data class New(val at: LocalDateTime? = null) : EditorRequest

    /** Edits [id]; [occurrence] is the instance the user opened, null for the event's own time. */
    data class Edit(val id: EventId, val occurrence: EventTime? = null) : EditorRequest

    fun encode(): List<String> = when (this) {
        is New -> listOf(NEW, at?.toEpochSecond(ZoneOffset.UTC)?.toString().orEmpty())
        is Edit -> listOf(EDIT, id.value.toString()) + encodeTime(occurrence)
    }

    companion object {
        private const val NEW = "new"
        private const val EDIT = "edit"
        private const val NONE = "none"
        private const val TIMED = "timed"
        private const val ALL_DAY = "allday"

        /** The request [encode] wrote, or null if [values] is not one. */
        fun decode(values: List<String>): EditorRequest? = runCatching {
            when (values.firstOrNull()) {
                NEW -> New(
                    values[1].toLongOrNull()?.let {
                        LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC)
                    }
                )

                EDIT -> Edit(EventId(values[1].toLong()), decodeTime(values.drop(2)))

                else -> null
            }
        }.getOrNull()

        private fun encodeTime(time: EventTime?): List<String> = when (time) {
            null -> listOf(NONE)

            is EventTime.Timed -> listOf(
                TIMED,
                time.start.toEpochMilli().toString(),
                time.end.toEpochMilli().toString(),
                time.zone.id
            )

            is EventTime.AllDay -> listOf(
                ALL_DAY,
                time.startDate.toEpochDay().toString(),
                time.endDate.toEpochDay().toString()
            )
        }

        private fun decodeTime(values: List<String>): EventTime? = when (values.first()) {
            TIMED -> EventTime.Timed(
                Instant.ofEpochMilli(values[1].toLong()),
                Instant.ofEpochMilli(values[2].toLong()),
                ZoneId.of(values[3])
            )

            ALL_DAY -> EventTime.AllDay(
                LocalDate.ofEpochDay(values[1].toLong()),
                LocalDate.ofEpochDay(values[2].toLong())
            )

            else -> null
        }
    }
}
