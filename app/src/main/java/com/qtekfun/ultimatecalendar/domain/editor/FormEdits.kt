// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.model.Reminder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

private const val MINUTES_PER_DAY = 24 * 60

/**
 * Moves the start to [moved] and the end with it, so the event keeps its length (as Google
 * Calendar does): timed events keep their elapsed time, which a clock change in between does
 * not alter; all-day events keep their number of days. A form that ends before it starts keeps
 * its end, so the user can mend it by moving the start.
 */
private fun EventForm.moveStart(moved: ZonedDateTime): EventForm {
    val length = length() ?: return copy(start = moved)
    val newEnd = if (allDay) {
        val days = ChronoUnit.DAYS.between(startDate, endDate)
        end.with(moved.toLocalDate().plusDays(days))
    } else {
        moved.plus(length)
    }
    return copy(start = moved, end = newEnd)
}

/**
 * Changing a date or a time keeps the other half and the offset the time had, so the second
 * pass of an hour that the clocks repeat stays the second pass; a time that a clock change
 * skips moves forward by the gap, so the form never shows a time the event cannot have.
 */
fun EventForm.withStartDate(date: LocalDate): EventForm = moveStart(start.with(date))

fun EventForm.withStartTime(time: LocalTime): EventForm = moveStart(start.with(time))

/** The end is set as given; an end before the start is reported by [EventForm.issues]. */
fun EventForm.withEndDate(date: LocalDate): EventForm = copy(end = end.with(date))

fun EventForm.withEndTime(time: LocalTime): EventForm = copy(end = end.with(time))

/**
 * Switches between a timed and an all-day event. Going all-day, an event that ends at midnight
 * ends the day before; going back, an event that would end at or before its start gets the
 * default length. Reminders that are still the defaults become the other kind's defaults;
 * others go to whole days (a reminder "30 minutes before" a day has no meaning) or stay as
 * they are.
 */
fun EventForm.withAllDay(value: Boolean): EventForm {
    if (value == allDay) return this
    val reminders = switchedReminders(value)
    return if (value) {
        val midnight = end.toLocalTime() == LocalTime.MIDNIGHT && endDate.isAfter(startDate)
        val last = if (midnight) endDate.minusDays(1) else endDate
        copy(allDay = true, end = end.with(last), reminders = reminders)
    } else {
        val timed = copy(allDay = false, reminders = reminders)
        if (timed.length()?.isZero != false) {
            timed.copy(end = timed.start.plus(defaults.duration))
        } else {
            timed
        }
    }
}

private fun EventForm.switchedReminders(toAllDay: Boolean): List<Reminder> = when {
    reminders == defaults.reminders(allDay) -> defaults.reminders(toAllDay)
    toAllDay -> reminders.map { it.copy(minutesBefore = wholeDays(it.minutesBefore)) }.distinct()
    else -> reminders
}

private fun wholeDays(minutes: Int): Int = (minutes + MINUTES_PER_DAY - 1) / MINUTES_PER_DAY *
    MINUTES_PER_DAY

/**
 * Shows the event in another time zone, keeping the wall-clock times: 15:00 stays 15:00, so the
 * moment it happens changes. This is how Google Calendar's "Time zone" field works (the zone
 * says where the times are meant, it does not convert them).
 */
fun EventForm.withZone(newZone: ZoneId): EventForm = copy(
    zone = newZone,
    start = ZonedDateTime.of(start.toLocalDateTime(), newZone),
    end = ZonedDateTime.of(end.toLocalDateTime(), newZone)
)
