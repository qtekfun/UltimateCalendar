// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.recurrence.RepeatEnd
import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Why a form cannot be saved yet. */
enum class FormIssue {
    /** The event ends before it starts. */
    END_BEFORE_START,

    /** No calendar was picked (none accepts events, or the list is not loaded). */
    NO_CALENDAR,

    /** The repetition ends before the event starts. */
    REPEAT_ENDS_BEFORE_START
}

/**
 * What a new event starts with (RF-10): the length of a timed event and the reminders of each
 * kind, in minutes before the event begins.
 */
data class EditorDefaults(
    val durationMinutes: Int = SettingsRules.DEFAULT_DURATION_MINUTES,
    val timedReminders: List<Int> = SettingsRules.DEFAULT_REMINDERS,
    val allDayReminders: List<Int> = SettingsRules.DEFAULT_ALL_DAY_REMINDERS
) {
    val duration: Duration get() = Duration.ofMinutes(durationMinutes.toLong())

    /** The reminders of a new event, as the source stores them. */
    fun reminders(allDay: Boolean): List<Reminder> =
        (if (allDay) allDayReminders else timedReminders).map { Reminder(it) }
}

/**
 * What the editor screen is editing (RF-05), as plain values the user typed or picked.
 *
 * [start] and [end] are times in [zone], whatever the event is: an all-day event only uses
 * their dates, and [end]'s date is the last day shown (inclusive), as Google Calendar shows it.
 * Keeping the times while an event is all-day lets the switch go back without losing them. They
 * are zoned, not bare wall-clock times, so that the second pass of an hour that clocks repeat
 * stays the second pass. Every change is a function that returns a new form, so the screen only
 * draws.
 */
data class EventForm(
    val title: String = "",
    val allDay: Boolean = false,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val zone: ZoneId,
    val location: String = "",
    val description: String = "",
    val calendarId: CalendarId? = null,
    /** ARGB, or null for the calendar's color. */
    val color: Int? = null,
    val availability: Availability = Availability.BUSY,
    /** In the order shown; all-day events count minutes before the day starts. */
    val reminders: List<Reminder> = emptyList(),
    /** Everyone invited, the organizer's own row included when the event came with one. */
    val attendees: List<Attendee> = emptyList(),
    val repeat: RepeatSetting = RepeatSetting.NEVER,
    /** The organizer of an existing event, shown read-only; null for a new one. */
    val organizer: String? = null,
    val defaults: EditorDefaults = EditorDefaults()
) {
    /** The invited people the user can edit: everyone but the organizer. */
    val guests: List<Attendee> get() = attendees.filterNot { it.isOrganizer }

    /** The problems that stop the form from being saved; empty when it can be. */
    val issues: Set<FormIssue>
        get() = buildSet {
            if (calendarId == null) add(FormIssue.NO_CALENDAR)
            if (endsBeforeStart()) add(FormIssue.END_BEFORE_START)
            if (repeatEndsBeforeStart()) add(FormIssue.REPEAT_ENDS_BEFORE_START)
        }

    val isValid: Boolean get() = issues.isEmpty()

    /** How long the event lasts, or null when it ends before it starts. */
    fun length(): Duration? = if (allDay) {
        Duration.ofDays(ChronoUnit.DAYS.between(startDate, endDate)).takeIf { !it.isNegative }
    } else {
        Duration.between(start, end).takeIf { !it.isNegative }
    }

    val startDate: LocalDate get() = start.toLocalDate()

    /** All-day: the last day; timed: the date the event ends on. */
    val endDate: LocalDate get() = end.toLocalDate()

    /** The time of the event as the source stores it; null while the form has an issue with it. */
    fun toEventTime(): EventTime? = when {
        endsBeforeStart() -> null
        allDay -> EventTime.AllDay(startDate, endDate.plusDays(1))
        else -> EventTime.Timed(start.toInstant(), end.toInstant(), zone)
    }

    /** The rule to store: all-day events write the end date as a date, timed ones as UTC. */
    fun rrule(): String? = repeat.toRrule(startDate, zone.takeUnless { allDay })

    private fun endsBeforeStart() = length() == null

    private fun repeatEndsBeforeStart(): Boolean {
        val custom = (repeat as? RepeatSetting.Custom)?.repeat ?: return false
        val until = custom.until
        return custom.end == RepeatEnd.ON_DATE && until != null && until.isBefore(startDate)
    }
}
