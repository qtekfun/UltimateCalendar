// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId

/** The source an id belongs to, which is how the composite routes a call. */
enum class SourceKind {
    PROVIDER,
    CALDAV,
    SUBSCRIPTION;

    companion object {
        fun of(value: Long): SourceKind = when {
            CalDavIds.isCalDav(value) -> CALDAV
            SubscriptionIds.isSubscription(value) -> SUBSCRIPTION
            else -> PROVIDER
        }

        fun of(id: CalendarId) = of(id.value)

        fun of(id: EventId) = of(id.value)
    }
}

/**
 * The ids of the ICS subscriptions (T39), apart from the other two sources: a subscription row
 * id with bit 61 set. [CalDavIds] uses bit 62, so the ranges never meet: a subscription id has
 * bit 62 clear, a CalDAV id has it set. Provider ids stay as they are, small positive numbers,
 * and negative values (the test reminder) belong to no source.
 */
object SubscriptionIds {
    private const val BIT = 1L shl 61
    private const val CALDAV_BIT = 1L shl 62

    /** Whether [value] is the id of a subscription row. */
    fun isSubscription(value: Long): Boolean =
        value > 0 && value and BIT != 0L && value and CALDAV_BIT == 0L

    /** The id the app uses for the subscription row [row]. */
    fun encode(row: Long): Long {
        require(row in 1 until BIT) { "Not a row id: $row" }
        return row or BIT
    }

    /** The Room row of a subscription id; fails for one that is not. */
    fun decode(value: Long): Long {
        require(isSubscription(value)) { "Not a subscription id: $value" }
        return value xor BIT
    }

    fun isSubscription(id: CalendarId) = isSubscription(id.value)

    fun isSubscription(id: EventId) = isSubscription(id.value)

    fun calendar(row: Long) = CalendarId(encode(row))

    fun event(row: Long) = EventId(encode(row))

    fun rowOf(id: CalendarId) = decode(id.value)

    fun rowOf(id: EventId) = decode(id.value)
}
