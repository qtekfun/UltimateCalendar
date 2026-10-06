// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.LocalDate

/**
 * Identifies one occurrence of a series by its original start, as `EXDATE`, `RDATE` and
 * `RECURRENCE-ID` do: a date for all-day series, a moment for timed ones. A key of the other kind
 * never matches anything.
 */
sealed interface OccurrenceKey {
    data class Day(val date: LocalDate) : OccurrenceKey

    data class Moment(val at: Instant) : OccurrenceKey
}

/** The key of the occurrence starting as this time does. */
internal fun EventTime.key(): OccurrenceKey = when (this) {
    is EventTime.AllDay -> OccurrenceKey.Day(startDate)
    is EventTime.Timed -> OccurrenceKey.Moment(start)
}

/**
 * A `RECURRENCE-ID` component: the occurrence at [recurrenceId] is replaced by [replacement] (a
 * moved or edited occurrence), or cancelled when [replacement] is null.
 */
data class OccurrenceOverride(val recurrenceId: OccurrenceKey, val replacement: Event? = null)

/**
 * A series as stored: the master [event] (its time is `DTSTART` and the length of each
 * occurrence, its [Event.rrule] the rule), the `EXDATE`s, the extra `RDATE`s and the overrides.
 */
data class EventSeries(
    val event: Event,
    val exDates: Set<OccurrenceKey> = emptySet(),
    val rDates: Set<OccurrenceKey> = emptySet(),
    val overrides: List<OccurrenceOverride> = emptyList()
)

/** Why a series could not be expanded. */
enum class UnsupportedReason {
    /** The RRULE text has parts the app does not handle (see [RecurrenceRules.parse]). */
    UNPARSEABLE_RULE,

    /** A weekday with an ordinal (`2TU`) outside a monthly or yearly rule, which RFC 5545 forbids. */
    ORDINAL_WEEKDAY
}

/** The outcome of [RecurrenceEngine.expand]; the engine never throws for rules it cannot read. */
sealed interface Expansion {
    /** Every instance that touches the range, sorted by start. */
    data class Complete(val instances: List<EventInstance>) : Expansion

    /**
     * The rule produced nothing for so long that the engine gave up; [instances] are the ones
     * found until then.
     */
    data class LimitReached(val instances: List<EventInstance>) : Expansion

    data class Unsupported(val reason: UnsupportedReason) : Expansion
}
