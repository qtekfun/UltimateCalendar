// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * What dragging an event on a day grid does to its time (RF-03, T18), as pure decisions. The
 * grid is a wall-clock grid in the device's zone, so every move is made on the wall clock and
 * turned back into moments with [WallClock] (the skipped hour of a clock change is never
 * landed on). An event keeps the exact length it had, in moments, and keeps its own zone: what
 * moves are its moments, not its zone label.
 *
 * Each function takes how far the finger went from where it grabbed the event, as days and
 * (fractional) minutes, and answers the time the event would have; the original time itself when
 * the finger has not gone far enough to change anything, so dragging back to the origin cancels.
 */
object EventDrag {
    /** Events snap to quarter hours. */
    const val STEP_MINUTES = 15

    /** The shortest an event can be made by dragging its end. */
    val MIN_DURATION: Duration = Duration.ofMinutes(STEP_MINUTES.toLong())

    private const val DAY = TimeScale.MINUTES_PER_DAY.toLong()

    /**
     * Moves a timed event by [dayDelta] days and [minuteDelta] minutes, snapping its start to
     * [step] minutes inside [bounds]. An event that fits in one day stays within a day (it is
     * kept from running over midnight); a longer one only has to start inside [bounds].
     */
    fun move(
        original: EventTime.Timed,
        zone: ZoneId,
        dayDelta: Int,
        minuteDelta: Float,
        bounds: DayBounds,
        step: Int = STEP_MINUTES
    ): EventTime.Timed {
        if (dayDelta == 0 && abs(minuteDelta) < step / 2f) return original
        val start = local(original.start, zone)
        val end = local(original.end, zone)
        val origin = bounds.first.atStartOfDay()
        val raw =
            ChronoUnit.MINUTES.between(origin, start) + dayDelta * DAY + minuteDelta.toDouble()
        val fitsInDay = !end.isAfter(start.toLocalDate().plusDays(1).atStartOfDay())
        val length = if (fitsInDay) ChronoUnit.MINUTES.between(start, end) else null
        val total = clampStart(snap(raw, step), length, bounds, step)
        val newStart = WallClock.resolve(origin.plusMinutes(total), zone)
        val kept = Duration.between(original.start, original.end)
        return EventTime.Timed(newStart, newStart.plus(kept), original.zone)
    }

    /**
     * Moves the end of a timed event by [minuteDelta] minutes, snapped to [step] and kept
     * between [MIN_DURATION] after the start and the end of the day it ends on.
     */
    fun resize(
        original: EventTime.Timed,
        zone: ZoneId,
        minuteDelta: Float,
        step: Int = STEP_MINUTES
    ): EventTime.Timed {
        if (abs(minuteDelta) < step / 2f) return original
        val start = local(original.start, zone)
        val end = local(original.end, zone)
        // An end at midnight closes the day before: that day's grid is where its handle is.
        val day = (if (end.isAfter(start)) end.minusNanos(1) else end).toLocalDate().atStartOfDay()
        val raw = ChronoUnit.MINUTES.between(day, end) + minuteDelta.toDouble()
        val shortest = original.start.plus(MIN_DURATION)
        val earliest = ceil(ChronoUnit.MINUTES.between(day, local(shortest, zone)), step)
        val minutes = snap(raw, step).coerceIn(earliest, maxOf(earliest, DAY))
        val newEnd = maxOf(WallClock.resolve(day.plusMinutes(minutes), zone), shortest)
        return EventTime.Timed(original.start, newEnd, original.zone)
    }

    /**
     * Moves an all-day event by [dayDelta] days, keeping its length, so that at least one of its
     * days stays inside [bounds]. An all-day event never becomes a timed one by dragging.
     */
    fun moveAllDay(original: EventTime.AllDay, dayDelta: Int, bounds: DayBounds): EventTime.AllDay {
        if (dayDelta == 0) return original
        val length = ChronoUnit.DAYS.between(original.startDate, original.endDate)
        val earliest = bounds.first.minusDays(length - 1)
        val wanted = original.startDate.plusDays(dayDelta.toLong())
        val start = when {
            wanted.isBefore(earliest) -> earliest
            wanted.isAfter(bounds.last) -> bounds.last
            else -> wanted
        }
        return EventTime.AllDay(start, start.plusDays(length))
    }

    private fun local(instant: Instant, zone: ZoneId): LocalDateTime =
        instant.atZone(zone).toLocalDateTime()

    private fun snap(minutes: Double, step: Int): Long = (minutes / step).roundToLong() * step

    private fun ceil(minutes: Long, step: Int): Long = (maxOf(minutes, 0L) + step - 1) / step * step

    /**
     * Keeps a snapped start (minutes from the first day's midnight) inside [bounds]. An event
     * that is [length] minutes long and fits in a day stays inside the day it lands on.
     */
    private fun clampStart(total: Long, length: Long?, bounds: DayBounds, step: Int): Long {
        val end = bounds.dayCount * DAY
        if (length == null) return total.coerceIn(0, end - step)
        val inside = total.coerceIn(0, end - 1)
        val latest = (DAY - maxOf(length, step.toLong())).coerceAtLeast(0) / step * step
        return inside / DAY * DAY + minOf(inside % DAY, latest)
    }
}
