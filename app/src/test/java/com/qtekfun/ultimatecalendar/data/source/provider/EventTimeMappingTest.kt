// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Events
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EventTimeMappingTest {
    private val utc = ZoneId.of("UTC")
    private val madrid = ZoneId.of("Europe/Madrid")
    private val start = Instant.parse("2026-10-06T10:00:00Z")

    @Test
    fun `a timed event keeps its instants and its own zone`() {
        val time = EventTimeMapping.read(
            allDay = false,
            startMs = start.toEpochMilli(),
            endMs = start.plusSeconds(3_600).toEpochMilli(),
            duration = null,
            timeZone = "Europe/Madrid"
        )

        assertEquals(EventTime.Timed(start, start.plusSeconds(3_600), madrid), time)
    }

    @Test
    fun `a series has a duration instead of an end`() {
        val time = EventTimeMapping.read(false, start.toEpochMilli(), null, "P3600S", "UTC")

        assertEquals(EventTime.Timed(start, start.plusSeconds(3_600), utc), time)
    }

    @Test
    fun `an end before the start, or none at all, is read as an event of no length`() {
        val before = EventTimeMapping.read(
            false,
            start.toEpochMilli(),
            start.toEpochMilli() - 1,
            null,
            "UTC"
        )
        val none = EventTimeMapping.read(false, start.toEpochMilli(), null, null, "UTC")

        assertEquals(EventTime.Timed(start, start, utc), before)
        assertEquals(EventTime.Timed(start, start, utc), none)
    }

    @Test
    fun `an unknown or missing zone is read as UTC`() {
        assertEquals(utc, EventTimeMapping.zoneOf(null))
        assertEquals(utc, EventTimeMapping.zoneOf(" "))
        assertEquals(utc, EventTimeMapping.zoneOf("Mars/Olympus"))
        assertEquals(madrid, EventTimeMapping.zoneOf("Europe/Madrid"))
    }

    @Test
    fun `all-day events are dates in UTC whatever the end looks like`() {
        val first = LocalDate.of(2026, 10, 6)
        val midnight = first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val day = 86_400_000L

        // End at the next midnight (exclusive), as Instances reports it.
        assertEquals(
            EventTime.AllDay(first, first.plusDays(2)),
            EventTimeMapping.read(true, midnight, midnight + 2 * day, null, "UTC")
        )
        // End on the last millisecond of the last day.
        assertEquals(
            EventTime.AllDay(first, first.plusDays(2)),
            EventTimeMapping.read(true, midnight, midnight + 2 * day - 1, null, "UTC")
        )
        // A series: no end, a duration in days.
        assertEquals(
            EventTime.AllDay(first, first.plusDays(3)),
            EventTimeMapping.read(true, midnight, null, "P3D", "UTC")
        )
        // No end at all, or one before the start: a single day.
        assertEquals(
            EventTime.AllDay(first, first.plusDays(1)),
            EventTimeMapping.read(true, midnight, null, null, "UTC")
        )
        assertEquals(
            EventTime.AllDay(first, first.plusDays(1)),
            EventTimeMapping.read(
                true,
                midnight,
                midnight - day,
                null,
                "UTC"
            )
        )
    }

    @Test
    fun `a single timed event is written with its end and its zone`() {
        val written = EventTimeMapping.write(
            EventTime.Timed(start, start.plusSeconds(60), madrid),
            repeats = false
        )

        assertEquals(
            mapOf(
                Events.ALL_DAY to 0,
                Events.DTSTART to start.toEpochMilli(),
                Events.DTEND to start.plusSeconds(60).toEpochMilli(),
                Events.DURATION to null,
                Events.EVENT_TIMEZONE to "Europe/Madrid"
            ),
            written
        )
    }

    @Test
    fun `a repeating timed event is written with a duration and no end`() {
        val written = EventTimeMapping.write(
            EventTime.Timed(start, start.plusSeconds(3_600), madrid),
            repeats = true
        )

        assertNull(written[Events.DTEND])
        assertEquals("P3600S", written[Events.DURATION])
    }

    @Test
    fun `an all-day event is written as UTC midnights`() {
        val time = EventTime.AllDay(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 8))
        val midnight = Instant.parse("2026-10-06T00:00:00Z").toEpochMilli()

        val single = EventTimeMapping.write(time, repeats = false)
        assertEquals(1, single[Events.ALL_DAY])
        assertEquals(midnight, single[Events.DTSTART])
        assertEquals(Instant.parse("2026-10-08T00:00:00Z").toEpochMilli(), single[Events.DTEND])
        assertEquals("UTC", single[Events.EVENT_TIMEZONE])

        val series = EventTimeMapping.write(time, repeats = true)
        assertNull(series[Events.DTEND])
        assertEquals("P2D", series[Events.DURATION])
    }

    @Test
    fun `durations are read in the forms the provider and other apps write`() {
        val expected = mapOf(
            "P3600S" to 3_600L,
            "PT1H30M" to 5_400L,
            "P1D" to 86_400L,
            "P2W" to 1_209_600L,
            "P1DT2H" to 93_600L,
            "+PT5M" to 300L,
            " P1D " to 86_400L
        )
        expected.forEach { (text, seconds) ->
            assertEquals(seconds, EventTimeMapping.parseDurationSeconds(text), text)
        }
        listOf(null, "", "P", "3600", "PT", "P1X", "P1Dfoo").forEach {
            assertNull(EventTimeMapping.parseDurationSeconds(it), "$it")
        }
    }
}
