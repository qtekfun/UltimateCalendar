// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import java.time.Instant

/**
 * A postponed reminder as one line of text, to keep it on the phone (RF-07). Fields are split by
 * a control character that [encode] removes from the text, so reading never gets lost.
 */
object SnoozeCodec {
    private const val SEPARATOR = '\u001F'
    private const val FIELDS = 9
    private val BOOLEANS = setOf("true", "false")

    fun encode(reminder: PlannedReminder): String = listOf(
        reminder.id,
        reminder.eventId.value,
        reminder.calendarId.value,
        reminder.start.toEpochMilli(),
        reminder.at.toEpochMilli(),
        reminder.allDay,
        clean(reminder.location),
        clean(reminder.joinUrl),
        clean(reminder.title)
    ).joinToString(SEPARATOR.toString())

    /** The reminder in [line], or null when it is not one this encoder wrote. */
    fun decode(line: String): PlannedReminder? {
        val parts = line.split(SEPARATOR, limit = FIELDS)
        val numbers = parts.take(NUMBERS).map { it.toLongOrNull() }
        if (parts.size != FIELDS || numbers.any { it == null } || parts[ALL_DAY] !in BOOLEANS) {
            return null
        }
        return PlannedReminder(
            id = numbers[0]!!,
            eventId = EventId(numbers[1]!!),
            calendarId = CalendarId(numbers[2]!!),
            title = parts[TITLE],
            location = parts[LOCATION].ifEmpty { null },
            start = Instant.ofEpochMilli(numbers[START]!!),
            allDay = parts[ALL_DAY].toBoolean(),
            at = Instant.ofEpochMilli(numbers[AT]!!),
            joinUrl = parts[JOIN].ifEmpty { null }
        )
    }

    private fun clean(text: String?): String = text.orEmpty().replace(SEPARATOR.toString(), "")

    private const val NUMBERS = 5
    private const val START = 3
    private const val AT = 4
    private const val ALL_DAY = 5
    private const val LOCATION = 6
    private const val JOIN = 7
    private const val TITLE = 8
}
