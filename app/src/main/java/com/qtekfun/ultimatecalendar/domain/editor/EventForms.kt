// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Builds the form for a new event, or for an existing one. */
object EventForms {
    private const val SLOT_MINUTES = 30L

    /** The time a new all-day event gets when the user turns "All day" off. */
    private val DEFAULT_TIME = LocalTime.of(9, 0)

    /**
     * [moment] rounded up to the next half hour (and left alone when it is on one), seconds
     * dropped: a quick-created event starts on a tidy time.
     */
    fun roundUpToSlot(moment: LocalDateTime): LocalDateTime {
        val minute = moment.truncatedTo(ChronoUnit.MINUTES)
        val past = minute.minute % SLOT_MINUTES
        val whole = past == 0L && minute == moment
        return if (whole) moment else minute.plusMinutes(SLOT_MINUTES - past)
    }

    /**
     * A new event in [calendar] (null when none accepts events yet). It starts at [at], the slot
     * the user tapped, or else the next half hour of [clock] in [zone], and lasts the default
     * length; [allDay] makes it a one-day event on that date.
     */
    fun create(
        clock: Clock,
        zone: ZoneId,
        defaults: EditorDefaults,
        calendar: CalendarInfo?,
        at: LocalDateTime? = null,
        allDay: Boolean = false
    ): EventForm {
        val start = existing(roundUpToSlot(at ?: LocalDateTime.now(clock.withZone(zone))), zone)
        val end = existing(start.atZone(zone).plus(defaults.duration).toLocalDateTime(), zone)
        return EventForm(
            allDay = allDay,
            start = start,
            end = if (allDay) start else end,
            zone = zone,
            calendarId = calendar?.id,
            reminders = defaults.reminders(allDay),
            organizer = null,
            defaults = defaults
        )
    }

    /**
     * The form for [event], showing its [occurrence] (any occurrence of a series; the event's
     * own time for a single event). Timed events keep their own zone, which may not be the
     * phone's [deviceZone]; all-day events use [deviceZone] for the hidden times.
     */
    fun edit(
        event: Event,
        occurrence: EventTime,
        deviceZone: ZoneId,
        defaults: EditorDefaults
    ): EventForm {
        val zone = (occurrence as? EventTime.Timed)?.zone ?: deviceZone
        val (start, end) = when (occurrence) {
            is EventTime.Timed -> occurrence.start.atZone(zone).toLocalDateTime() to
                occurrence.end.atZone(zone).toLocalDateTime()

            is EventTime.AllDay -> occurrence.startDate.atTime(DEFAULT_TIME) to
                occurrence.lastDate.atTime(DEFAULT_TIME)
        }
        val anchor: LocalDate = start.toLocalDate()
        return EventForm(
            title = event.title,
            allDay = occurrence is EventTime.AllDay,
            start = start,
            end = end,
            zone = zone,
            location = event.location.orEmpty(),
            description = event.description.orEmpty(),
            calendarId = event.calendarId,
            color = event.color,
            availability = event.availability,
            reminders = event.reminders,
            attendees = event.attendees,
            repeat = RepeatSetting.of(event.rrule, anchor, zone),
            organizer = event.organizer,
            defaults = defaults
        )
    }
}

/**
 * The draft that stores [this] form, or null while it has [EventForm.issues]. Blank text fields
 * are stored as absent, and so is the organizer's attendee row of a new event (the source adds
 * it).
 */
fun EventForm.toDraft(): EventDraft? {
    val time = toEventTime()
    val calendar = calendarId
    return if (!isValid || time == null || calendar == null) {
        null
    } else {
        EventDraft(
            calendarId = calendar,
            title = title.trim(),
            time = time,
            location = location.trim().ifEmpty { null },
            description = description.trim().ifEmpty { null },
            color = color,
            availability = availability,
            rrule = rrule(),
            attendees = attendees,
            reminders = reminders
        )
    }
}
