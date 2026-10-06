// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

internal object SeriesTimes {
    /**
     * The series' own time after the user moved one occurrence from [occurrence] to [edited]:
     * the same shift in days, at the edited time of day and length. Dates are compared on the
     * wall clock of each time's zone, so a daylight saving change does not skew the series.
     */
    fun rebase(occurrence: EventTime, edited: EventTime, series: EventTime): EventTime {
        val shift = ChronoUnit.DAYS.between(dateOf(occurrence), dateOf(edited))
        val day = dateOf(series).plusDays(shift)
        return when (edited) {
            is EventTime.AllDay -> EventTime.AllDay(
                day,
                day.plusDays(ChronoUnit.DAYS.between(edited.startDate, edited.endDate))
            )

            is EventTime.Timed -> {
                val start = LocalDateTime.of(day, edited.start.atZone(edited.zone).toLocalTime())
                    .atZone(edited.zone).toInstant()
                val length = Duration.between(edited.start, edited.end)
                EventTime.Timed(start, start.plus(length), edited.zone)
            }
        }
    }

    private fun dateOf(time: EventTime): LocalDate = when (time) {
        is EventTime.AllDay -> time.startDate
        is EventTime.Timed -> time.start.atZone(time.zone).toLocalDate()
    }
}
