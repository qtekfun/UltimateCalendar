// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId

/**
 * Keeps the ids of the two sources apart. The Android provider's ids are small positive numbers
 * and stay as they are (so everything stored for them, such as the local settings of a calendar,
 * stays valid); a CalDAV id is its Room row id with bit 62 set. Provider ids never get near that
 * bit, and negative values (the test reminder) belong to neither source.
 */
object CalDavIds {
    private const val BIT = 1L shl 62

    /** Whether [value] is the id of a CalDAV row. */
    fun isCalDav(value: Long): Boolean = value > 0 && value and BIT != 0L

    /** The id the app uses for the CalDAV row [row]. */
    fun encode(row: Long): Long {
        require(row in 1 until BIT) { "Not a row id: $row" }
        return row or BIT
    }

    /** The Room row of a CalDAV id; fails for one that is not. */
    fun decode(value: Long): Long {
        require(isCalDav(value)) { "Not a CalDAV id: $value" }
        return value xor BIT
    }

    fun isCalDav(id: CalendarId) = isCalDav(id.value)

    fun isCalDav(id: EventId) = isCalDav(id.value)

    fun calendar(row: Long) = CalendarId(encode(row))

    fun event(row: Long) = EventId(encode(row))

    fun rowOf(id: CalendarId) = decode(id.value)

    fun rowOf(id: EventId) = decode(id.value)
}
