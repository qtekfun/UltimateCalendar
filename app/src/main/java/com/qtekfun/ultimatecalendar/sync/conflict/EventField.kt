// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.conflict

import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.domain.model.Event

/**
 * The fields of an event that can change on either side, each with its bit in
 * `DavEventEntity.dirtyFields` and how to read and set it on an [IcsEvent]. The recurrence
 * exceptions are one field, [OVERRIDES], but the resolver merges them occurrence by occurrence.
 */
enum class EventField(val read: (IcsEvent) -> Any?, val write: (IcsEvent, IcsEvent) -> IcsEvent) {
    TITLE({ it.master.title }, { to, from -> to.edit { copy(title = from.master.title) } }),
    DESCRIPTION(
        { it.master.description },
        { to, from -> to.edit { copy(description = from.master.description) } }
    ),
    LOCATION(
        { it.master.location },
        { to, from -> to.edit { copy(location = from.master.location) } }
    ),
    TIME({ it.master.time }, { to, from -> to.edit { copy(time = from.master.time) } }),

    /** `RRULE` with its `EXDATE`s and `RDATE`s: they only make sense together. */
    REPEAT(
        { Triple(it.master.rrule, it.series.exDates, it.series.rDates) },
        { to, from ->
            to.edit { copy(rrule = from.master.rrule) }.let {
                it.copy(
                    series = it.series.copy(
                        exDates = from.series.exDates,
                        rDates = from.series.rDates
                    )
                )
            }
        }
    ),
    AVAILABILITY(
        { it.master.availability },
        { to, from -> to.edit { copy(availability = from.master.availability) } }
    ),

    /** The organizer and the guests with their answers. */
    ATTENDEES(
        { it.master.organizer to it.master.attendees },
        { to, from ->
            to.edit { copy(organizer = from.master.organizer, attendees = from.master.attendees) }
        }
    ),
    REMINDERS(
        { it.master.reminders },
        { to, from -> to.edit { copy(reminders = from.master.reminders) } }
    ),
    OVERRIDES(
        { it.series.overrides },
        { to, from -> to.copy(series = to.series.copy(overrides = from.series.overrides)) }
    );

    val bit: Int get() = 1 shl ordinal

    /** Title, description and location are never overwritten when both sides changed them (SPEC §5, rule 1). */
    val isText: Boolean get() = this == TITLE || this == DESCRIPTION || this == LOCATION

    companion object {
        fun fromBits(bits: Int): Set<EventField> = entries.filter { bits and it.bit != 0 }.toSet()

        fun toBits(fields: Set<EventField>): Int = fields.fold(0) { bits, field ->
            bits or field.bit
        }
    }
}

private val IcsEvent.master: Event get() = series.event

private fun IcsEvent.edit(change: Event.() -> Event): IcsEvent =
    copy(series = series.copy(event = series.event.change()))
