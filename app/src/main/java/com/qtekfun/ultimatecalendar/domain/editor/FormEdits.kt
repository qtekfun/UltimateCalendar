// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.editor

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.settings.SettingsRules
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val MINUTES_PER_DAY = 24 * 60

/**
 * A wall-clock time that exists in [zone]: one skipped by a clock change moves forward by the
 * gap, as the platform does, so the form never shows a time the event cannot have.
 */
internal fun existing(wall: LocalDateTime, zone: ZoneId): LocalDateTime =
    wall.atZone(zone).toLocalDateTime()

/**
 * Moves the start to [newStart] and the end with it, so the event keeps its length (as Google
 * Calendar does): timed events keep their elapsed time, which a clock change in between does
 * not alter; all-day events keep their number of days. A form that ends before it starts keeps
 * its end, so the user can mend it by moving the start.
 */
private fun EventForm.moveStart(newStart: LocalDateTime): EventForm {
    val moved = existing(newStart, zone)
    val length = length() ?: return copy(start = moved)
    val newEnd = if (allDay) {
        val days = ChronoUnit.DAYS.between(startDate, endDate)
        moved.toLocalDate().plusDays(days).atTime(end.toLocalTime())
    } else {
        instantOf(moved).plus(length).atZone(zone).toLocalDateTime()
    }
    return copy(start = moved, end = newEnd)
}

fun EventForm.withStartDate(date: LocalDate): EventForm =
    moveStart(date.atTime(start.toLocalTime()))

fun EventForm.withStartTime(time: LocalTime): EventForm =
    moveStart(startDate.atTime(time))

/** The end is set as given; an end before the start is reported by [EventForm.issues]. */
fun EventForm.withEndDate(date: LocalDate): EventForm =
    copy(end = existing(date.atTime(end.toLocalTime()), zone))

fun EventForm.withEndTime(time: LocalTime): EventForm =
    copy(end = existing(endDate.atTime(time), zone))

/**
 * Switches between a timed and an all-day event. Going all-day, an event that ends at midnight
 * ends the day before; going back, an event that would end at or before its start gets the
 * default length. Reminders that are still the defaults become the other kind's defaults; others go to
 * whole days (a reminder "30 minutes before" a day has no meaning) or stay as they are.
 */
fun EventForm.withAllDay(value: Boolean): EventForm {
    if (value == allDay) return this
    val reminders = switchedReminders(value)
    return if (value) {
        val midnight = end.toLocalTime() == LocalTime.MIDNIGHT && endDate.isAfter(startDate)
        val last = if (midnight) endDate.minusDays(1) else endDate
        copy(allDay = true, end = last.atTime(end.toLocalTime()), reminders = reminders)
    } else {
        val timed = copy(allDay = false, reminders = reminders)
        if (timed.length()?.isZero != false) {
            timed.copy(end = timed.instantOf(start).plus(defaults.duration).atZone(zone).toLocalDateTime())
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
    start = existing(start, newZone),
    end = existing(end, newZone)
)

fun EventForm.withCalendar(id: CalendarId): EventForm = copy(calendarId = id)

fun EventForm.withReminder(reminder: Reminder): EventForm = when {
    reminder in reminders || reminders.size >= SettingsRules.MAX_REMINDERS -> this
    reminder.minutesBefore > SettingsRules.MAX_REMINDER_MINUTES -> this
    else -> copy(reminders = (reminders + reminder).sortedBy { it.minutesBefore })
}

fun EventForm.withoutReminder(reminder: Reminder): EventForm =
    copy(reminders = reminders - reminder)

fun EventForm.withRepeat(setting: RepeatSetting): EventForm = copy(repeat = setting)
