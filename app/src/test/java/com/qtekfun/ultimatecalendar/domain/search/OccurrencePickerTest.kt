// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OccurrencePickerTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-10-06T12:00:00Z")

    private fun timed(start: String, minutes: Long = 60): EventInstance {
        val from = Instant.parse(start)
        return EventInstance(
            EventId(1),
            CalendarId(1),
            "Series",
            EventTime.Timed(from, from.plusSeconds(minutes * 60), ZoneOffset.UTC),
            isRecurring = true
        )
    }

    private fun allDay(from: String, to: String) = EventInstance(
        EventId(1),
        CalendarId(1),
        "Trip",
        EventTime.AllDay(LocalDate.parse(from), LocalDate.parse(to))
    )

    private fun pick(
        vararg occurrences: EventInstance,
        fallback: EventInstance = timed("2026-01-01T09:00:00Z")
    ) = OccurrencePicker.pick(now, madrid, occurrences.toList(), fallback)

    @Test
    fun `the next occurrence is chosen, not a later one or a past one`() {
        val past = timed("2026-10-01T09:00:00Z")
        val next = timed("2026-10-08T09:00:00Z")
        val later = timed("2026-10-15T09:00:00Z")

        val chosen = pick(later, past, next)

        assertEquals(next, chosen.instance)
        assertTrue(chosen.upcoming)
    }

    @Test
    fun `an occurrence in progress still counts as coming`() {
        val running = timed("2026-10-06T11:30:00Z", minutes = 90)
        val tomorrow = timed("2026-10-07T09:00:00Z")

        assertEquals(running, pick(tomorrow, running).instance)
    }

    @Test
    fun `one that ended a moment ago is over`() {
        val ended = timed("2026-10-06T10:00:00Z", minutes = 119)
        val chosen = pick(ended)

        assertEquals(ended, chosen.instance)
        assertFalse(chosen.upcoming)
    }

    @Test
    fun `an occurrence that ends exactly now has not ended`() {
        val ending = timed("2026-10-06T11:00:00Z", minutes = 60)

        assertTrue(pick(ending).upcoming)
    }

    @Test
    fun `when everything is over the latest occurrence is chosen`() {
        val old = timed("2026-03-01T09:00:00Z")
        val latest = timed("2026-09-20T09:00:00Z")
        val older = timed("2026-06-01T09:00:00Z")

        val chosen = pick(old, latest, older)

        assertEquals(latest, chosen.instance)
        assertFalse(chosen.upcoming)
    }

    @Test
    fun `without occurrences the series' own first one stands in`() {
        val fallback = timed("2030-01-01T09:00:00Z")

        val chosen = pick(fallback = fallback)

        assertEquals(fallback, chosen.instance)
        assertTrue(chosen.upcoming)
        assertFalse(pick(fallback = timed("2020-01-01T09:00:00Z")).upcoming)
    }

    @Test
    fun `an all-day event lasts until its last day is over`() {
        val today = allDay("2026-10-06", "2026-10-07")
        val yesterday = allDay("2026-10-05", "2026-10-06")
        val multi = allDay("2026-10-04", "2026-10-08")

        assertTrue(pick(today).upcoming)
        assertFalse(pick(yesterday).upcoming)
        assertTrue(pick(multi).upcoming)
    }

    @Test
    fun `the day an all-day event is on depends on the phone's zone`() {
        val lateEvening = Instant.parse("2026-10-06T22:30:00Z")
        val today = allDay("2026-10-06", "2026-10-07")

        // 22:30 UTC is already the 7th in Madrid (UTC+2) and still the 6th in UTC.
        val inUtc = OccurrencePicker.pick(lateEvening, ZoneOffset.UTC, listOf(today), today)
        val inMadrid = OccurrencePicker.pick(lateEvening, madrid, listOf(today), today)

        assertTrue(inUtc.upcoming)
        assertFalse(inMadrid.upcoming)
    }

    @Test
    fun `timed and all-day occurrences are ordered by their start in the phone's zone`() {
        val trip = allDay("2026-10-08", "2026-10-09")
        val meeting = timed("2026-10-08T10:00:00Z")

        assertEquals(trip, pick(meeting, trip).instance)
    }
}
