// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.navigation.DateRange
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Turns the instances of a range into what a day grid draws (RF-03). The source has already
 * expanded repetitions; nothing is expanded here.
 *
 * The grid is a wall-clock day: an event is placed at the local time its start and end show in
 * the device's zone. On a 23 or 25 hour day the grid still has 24 hours, so the columns of a
 * multi-day view line up; the skipped hour is empty and the repeated hour shows both passes
 * overlaid (the layout then puts overlapping events side by side).
 */
object TimeGridLayout {
    /** The shortest an event is drawn and laid out: a 15-minute or zero-length event stays visible. */
    val MIN_DURATION: Duration = Duration.ofMinutes(MIN_DURATION_MINUTES)

    private const val MIN_DURATION_MINUTES = 30L

    fun build(
        range: DateRange,
        zone: ZoneId,
        instances: List<EventInstance>,
        calendarColors: Map<CalendarId, Int> = emptyMap(),
        minDuration: Duration = MIN_DURATION
    ): TimeGridPage {
        val days = generateSequence(range.start) { it.plusDays(1) }
            .takeWhile { it.isBefore(range.endExclusive) }
            .toList()
        fun colorOf(instance: EventInstance) = instance.color ?: calendarColors[instance.calendarId]
        val allDay = allDayBars(range, days.size, instances, ::colorOf)
        val timed = days.flatMapIndexed { index, day ->
            val segments = instances.mapNotNull { instance ->
                val time = instance.time as? EventTime.Timed ?: return@mapNotNull null
                segmentOn(day, instance, time, zone)
                    ?.copy(dayIndex = index, color = colorOf(instance))
            }
            OverlapLayout.arrange(
                segments,
                start = { it.startMinute.toLong() },
                end = { it.endMinute.toLong() },
                minLength = minDuration.toMinutes()
            ).map { it.item.copy(column = it.column, columns = it.columns, span = it.span) }
        }
        return TimeGridPage(days, allDay, timed)
    }

    /** The minute of the day [instant] shows in [zone], seconds dropped. */
    fun minuteOfDay(instant: Instant, zone: ZoneId): Int {
        val time = instant.atZone(zone)
        return time.hour * TimeScale.MINUTES_PER_HOUR + time.minute
    }

    /** The calendar day [instant] falls on in [zone]. */
    fun dateOf(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

    private fun allDayBars(
        range: DateRange,
        dayCount: Int,
        instances: List<EventInstance>,
        colorOf: (EventInstance) -> Int?
    ): List<AllDayBar> {
        val bars = instances.mapNotNull { instance ->
            val time = instance.time as? EventTime.AllDay ?: return@mapNotNull null
            if (!time.startDate.isBefore(range.endExclusive) ||
                !time.endDate.isAfter(range.start)
            ) {
                return@mapNotNull null
            }
            AllDayBar(
                instance = instance,
                firstDay = indexOf(range.start, time.startDate).coerceAtLeast(0),
                lastDay = indexOf(range.start, time.lastDate).coerceAtMost(dayCount - 1),
                row = 0,
                color = colorOf(instance),
                continuesBefore = time.startDate.isBefore(range.start),
                continuesAfter = !time.lastDate.isBefore(range.endExclusive)
            )
        }
        return OverlapLayout.arrange(
            bars,
            start = { it.firstDay.toLong() },
            end = { it.lastDay + 1L }
        ).map { it.item.copy(row = it.column) }
    }

    private fun indexOf(first: LocalDate, date: LocalDate): Int =
        ChronoUnit.DAYS.between(first, date).toInt()

    /**
     * The part of a timed [instance] on [day], or null when it does not touch that day. The
     * caller sets the day's index and the color.
     */
    private fun segmentOn(
        day: LocalDate,
        instance: EventInstance,
        time: EventTime.Timed,
        zone: ZoneId
    ): TimedBlock? {
        val dayStart = day.atStartOfDay(zone).toInstant()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
        val zeroLength = time.start == time.end
        val touches = time.start < dayEnd &&
            (time.end > dayStart || (zeroLength && time.start >= dayStart))
        if (!touches) return null
        val startMinute = if (time.start <= dayStart) 0 else minuteOfDay(time.start, zone)
        val endMinute = if (time.end >= dayEnd) {
            TimeScale.MINUTES_PER_DAY
        } else {
            maxOf(minuteOfDay(time.end, zone), startMinute)
        }
        return TimedBlock(
            instance = instance,
            dayIndex = 0,
            startMinute = startMinute,
            endMinute = endMinute,
            column = 0,
            columns = 1,
            span = 1,
            color = null,
            continuesBefore = time.start < dayStart,
            continuesAfter = time.end > dayEnd,
            otherZone = otherZoneOf(time, zone)
        )
    }

    /** The event's zone when it shows a different wall-clock time there than on the device. */
    private fun otherZoneOf(time: EventTime.Timed, device: ZoneId): ZoneId? =
        time.zone.takeIf { it.rules.getOffset(time.start) != device.rules.getOffset(time.start) }
}
