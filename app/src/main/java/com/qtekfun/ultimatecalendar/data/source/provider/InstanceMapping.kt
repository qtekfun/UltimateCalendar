// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.TimeRange

/** `Instances` rows (one per occurrence, already expanded by the provider) to [EventInstance]. */
internal object InstanceMapping {
    val projection = listOf(
        Instances.EVENT_ID,
        Instances.BEGIN,
        Instances.END,
        Instances.TITLE,
        Instances.EVENT_LOCATION,
        Instances.EVENT_COLOR,
        Instances.ALL_DAY,
        Instances.EVENT_TIMEZONE,
        Instances.CALENDAR_ID,
        Instances.RRULE,
        Instances.RDATE,
        Instances.ORIGINAL_ID,
        Instances.SELF_ATTENDEE_STATUS,
        Instances.STATUS
    )

    /** `Events.SELF_ATTENDEE_STATUS`: the user's answer, or null when the user is no attendee. */
    fun selfStatusOf(code: Int?): AttendeeStatus? =
        code?.takeIf { it != Attendees.ATTENDEE_STATUS_NONE }?.let { AttendeeMapping.statusOf(it) }

    /** The instance's start in epoch milliseconds, or null when the row has none. */
    fun beginOf(row: ProviderRow): Long? = row.long(Instances.BEGIN)

    /**
     * The instance of [row] if it is shown in [range], else null. The provider's range is
     * inclusive at both ends, so occurrences that merely touch it are dropped here, and so are
     * cancelled ones. An occurrence changed on its own is a separate event of the provider (it
     * has an `ORIGINAL_ID`); it is reported under the id of its series, as the contract needs.
     */
    fun toInstance(row: ProviderRow, range: TimeRange): EventInstance? {
        val begin = row.long(Instances.BEGIN)
        val end = row.long(Instances.END) ?: begin
        val eventId = row.long(Instances.ORIGINAL_ID) ?: row.long(Instances.EVENT_ID)
        val calendar = row.long(Instances.CALENDAR_ID)
        val usable = begin != null && end != null && eventId != null && calendar != null &&
            row.int(Instances.STATUS) != Events.STATUS_CANCELED &&
            overlaps(begin, end, range)
        return if (usable) {
            EventInstance(
                eventId = EventId(requireNotNull(eventId)),
                calendarId = CalendarId(requireNotNull(calendar)),
                title = row.text(Instances.TITLE).orEmpty(),
                time = EventTimeMapping.read(
                    allDay = row.flag(Instances.ALL_DAY),
                    startMs = requireNotNull(begin),
                    endMs = end,
                    duration = null,
                    timeZone = row.text(Instances.EVENT_TIMEZONE)
                ),
                location = row.text(Instances.EVENT_LOCATION)?.ifEmpty { null },
                color = row.int(Instances.EVENT_COLOR)?.let { CalendarMapping.opaque(it) },
                isRecurring = EventMapping.repeats(row) || row.long(Instances.ORIGINAL_ID) != null,
                selfStatus = selfStatusOf(row.int(Instances.SELF_ATTENDEE_STATUS))
            )
        } else {
            null
        }
    }

    /** Whether [beginMs] to [endMs] overlaps the half-open [range]; a moment counts at its start. */
    fun overlaps(beginMs: Long, endMs: Long, range: TimeRange): Boolean {
        val from = range.start.toEpochMilli()
        val to = range.end.toEpochMilli()
        return if (endMs <= beginMs) beginMs in from until to else beginMs < to && endMs > from
    }
}
