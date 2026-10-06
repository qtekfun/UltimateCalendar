// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.recurrence

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SeriesPositionTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val first = Instant.parse("2026-10-05T07:00:00Z")

    private fun series(rrule: String?, time: EventTime = timed(0)) =
        Event(EventId(1), CalendarId(1), "Standup", time, rrule = rrule)

    private fun timed(day: Long) = EventTime.Timed(
        first.plusSeconds(day * 86_400),
        first.plusSeconds(day * 86_400 + 1800),
        zone
    )

    @Test
    fun `occurrences before the third are the first two`() {
        val master = series("FREQ=DAILY;COUNT=5")

        assertEquals(2, SeriesPosition.before(master, timed(2), zone))
        assertEquals(4, SeriesPosition.before(master, timed(4), zone))
    }

    @Test
    fun `nothing comes before the first occurrence`() {
        assertEquals(0, SeriesPosition.before(series("FREQ=DAILY;COUNT=5"), timed(0), zone))
    }

    @Test
    fun `a weekly series counts by the week`() {
        val master = series("FREQ=WEEKLY;COUNT=10")

        assertEquals(3, SeriesPosition.before(master, timed(21), zone))
    }

    @Test
    fun `a rule the app does not understand counts nothing`() {
        assertEquals(0, SeriesPosition.before(series("FREQ=SECONDLY"), timed(3), zone))
    }

    @Test
    fun `all day series count by day`() {
        val start = LocalDate.of(2026, 10, 5)
        val master = series("FREQ=DAILY;COUNT=7", EventTime.AllDay(start, start.plusDays(1)))
        val fourth = EventTime.AllDay(start.plusDays(3), start.plusDays(4))

        assertEquals(3, SeriesPosition.before(master, fourth, zone))
    }
}
