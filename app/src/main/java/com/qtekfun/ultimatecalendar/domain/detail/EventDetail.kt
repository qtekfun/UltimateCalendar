// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.detail

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderLinks
import java.time.ZoneId

/**
 * Everything the detail screen (RF-04) shows about one occurrence of an event, decided here so
 * the screen only draws it. Build it with [EventDetails.build].
 *
 * [event] is as stored (the series itself for a repeating event); [occurrence] is the one the
 * user opened. [me] are the user's addresses in this calendar, [zone] the phone's zone.
 */
data class EventDetail(
    val event: Event,
    val calendar: CalendarInfo?,
    val occurrence: EventTime,
    val zone: ZoneId,
    val me: Set<String>,
    val time: DetailTime,
    val repeat: List<RepeatPhrase>,
    /** The place as written, or null when empty. */
    val location: String?,
    /** A `geo:` link for a place that is not a web address. */
    val mapUri: String?,
    /** The web address when the place is one. */
    val locationUrl: String?,
    /** The video call found in the place or the description. */
    val joinUrl: String?,
    val description: String?,
    val descriptionLinks: List<ReminderLinks.Link>,
    val reminders: List<ReminderLine>,
    val attendees: AttendeeGroups?,
    /** The user's own attendee entry, or null when not invited. */
    val self: Attendee?,
    val canRespond: Boolean,
    val canEdit: Boolean
) {
    val title: String get() = event.title

    /** An occurrence of a series: edits and deletions must ask which occurrences. */
    val isSeries: Boolean get() = event.isRecurring

    /** The color to draw with: the event's own, else its calendar's. */
    val color: Int? get() = event.color ?: calendar?.color

    /** The detail as if the user had answered [status] (the optimistic view of a response). */
    fun answered(status: AttendeeStatus): EventDetail = EventDetails.build(
        event.copy(
            attendees = event.attendees.map {
                if (it.isOneOf(me)) it.copy(status = status) else it
            }
        ),
        calendar,
        occurrence,
        zone,
        me
    )
}

object EventDetails {
    /**
     * Builds the detail of the [occurrence] of [event] in [calendar] (null when the calendar is
     * unknown, which allows nothing). [aliases] are the user's own addresses from Settings.
     */
    fun build(
        event: Event,
        calendar: CalendarInfo?,
        occurrence: EventTime,
        zone: ZoneId,
        aliases: Collection<String>
    ): EventDetail {
        val me = (aliases + listOfNotNull(calendar?.ownerEmail)).map(Attendee::normalize).toSet()
        val self = event.attendees.firstOrNull { it.isOneOf(me) }
        val location = event.location?.trim()?.takeIf { it.isNotEmpty() }
        val description = event.description?.trim()?.takeIf { it.isNotEmpty() }
        val access = calendar?.access
        return EventDetail(
            event = event,
            calendar = calendar,
            occurrence = occurrence,
            zone = zone,
            me = me,
            time = DetailTime.of(occurrence, zone),
            repeat = RepeatDescriber.describe(event.rrule, zone),
            location = location,
            mapUri = ReminderLinks.map(location),
            locationUrl = ReminderLinks.links(location).firstOrNull()?.url,
            joinUrl = ReminderLinks.videoCall(location, description),
            description = description,
            descriptionLinks = ReminderLinks.links(description),
            reminders = ReminderLine.of(event.reminders, event.isAllDay),
            attendees = AttendeeGrouping.group(event, me),
            self = self,
            canRespond = self != null && access?.canRespond == true,
            canEdit = access?.canEdit == true
        )
    }
}
