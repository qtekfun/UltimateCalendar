// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.detail.DetailTime
import java.time.ZonedDateTime
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** When an occurrence is, in words: the day(s), the hours, and the event's own zone if other. */
internal class TimeWords(
    private val words: WordSource,
    private val locale: Locale,
    private val dates: DateWords
) {
    fun lines(time: DetailTime): List<String> = when (time) {
        is DetailTime.AllDay -> listOf(
            if (time.isSingleDay) {
                dates.date(time.first, FormatStyle.FULL)
            } else {
                words.text(
                    R.string.detail_range,
                    dates.date(time.first, FormatStyle.FULL),
                    dates.date(time.last, FormatStyle.FULL)
                )
            },
            words.text(R.string.detail_all_day)
        )

        is DetailTime.Timed -> timed(time.start, time.end) + listOfNotNull(time.other?.let(::zone))
    }

    private fun zone(other: DetailTime.InOtherZone): String = words.text(
        R.string.detail_zone_line,
        other.zone.getDisplayName(TextStyle.FULL, locale),
        timed(other.start, other.end).joinToString(", ")
    )

    private fun timed(start: ZonedDateTime, end: ZonedDateTime?): List<String> = when {
        end == null -> listOf(
            dates.date(start.toLocalDate(), FormatStyle.FULL),
            dates.time(start.toLocalTime())
        )

        start.toLocalDate() == end.toLocalDate() -> listOf(
            dates.date(start.toLocalDate(), FormatStyle.FULL),
            words.text(
                R.string.detail_range,
                dates.time(start.toLocalTime()),
                dates.time(end.toLocalTime())
            )
        )

        else -> listOf(
            words.text(R.string.detail_range, dates.dateTime(start), dates.dateTime(end))
        )
    }
}
