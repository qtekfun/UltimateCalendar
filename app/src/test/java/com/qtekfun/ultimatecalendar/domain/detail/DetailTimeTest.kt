// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.detail

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetailTimeTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun `a day and a few days`() {
        val one = DetailTime.of(
            EventTime.AllDay(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-07")),
            madrid
        ) as DetailTime.AllDay
        assertTrue(one.isSingleDay)
        val several = DetailTime.of(
            EventTime.AllDay(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-09")),
            madrid
        ) as DetailTime.AllDay
        assertFalse(several.isSingleDay)
        assertEquals(LocalDate.parse("2026-10-08"), several.last)
    }

    @Test
    fun `a timed event in the phone's zone has no other zone`() {
        val time = DetailTime.of(
            EventTime.Timed(
                Instant.parse("2026-10-08T07:00:00Z"),
                Instant.parse("2026-10-08T08:30:00Z"),
                madrid
            ),
            madrid
        ) as DetailTime.Timed
        assertEquals(LocalTime.of(9, 0), time.start.toLocalTime())
        assertEquals(LocalTime.of(10, 30), time.end?.toLocalTime())
        assertTrue(time.isSameDay)
        assertNull(time.other)
    }

    @Test
    fun `an event of another zone is shown in the phone's and in its own`() {
        val time = DetailTime.of(
            EventTime.Timed(
                Instant.parse("2026-10-08T14:00:00Z"),
                Instant.parse("2026-10-08T15:00:00Z"),
                newYork
            ),
            madrid
        ) as DetailTime.Timed
        assertEquals(LocalTime.of(16, 0), time.start.toLocalTime())
        assertEquals(newYork, time.other?.zone)
        assertEquals(LocalTime.of(10, 0), time.other?.start?.toLocalTime())
        assertEquals(LocalTime.of(11, 0), time.other?.end?.toLocalTime())
    }

    @Test
    fun `an event that crosses midnight is not of one day`() {
        val time = DetailTime.of(
            EventTime.Timed(
                Instant.parse("2026-10-08T21:00:00Z"),
                Instant.parse("2026-10-08T23:00:00Z"),
                madrid
            ),
            madrid
        ) as DetailTime.Timed
        assertFalse(time.isSameDay)
    }

    @Test
    fun `a moment without length has no end`() {
        val moment = Instant.parse("2026-10-08T07:00:00Z")
        val time = DetailTime.of(
            EventTime.Timed(moment, moment, newYork),
            madrid
        ) as DetailTime.Timed
        assertNull(time.end)
        assertNull(time.other?.end)
        assertTrue(time.isSameDay)
    }

    @Test
    fun `all day reminders become days before at a time of day`() {
        val lines = ReminderLine.of(
            listOf(Reminder(900), Reminder(0, ReminderMethod.EMAIL), Reminder(2880 - 600)),
            allDay = true
        )
        assertEquals(
            listOf(
                Triple(0, LocalTime.MIDNIGHT, ReminderMethod.EMAIL),
                Triple(1, LocalTime.of(9, 0), ReminderMethod.ALERT),
                Triple(2, LocalTime.of(10, 0), ReminderMethod.ALERT)
            ),
            lines.map {
                val offset = requireNotNull(it.allDay)
                Triple(offset.daysBefore, offset.at, it.method)
            }
        )
    }

    @Test
    fun `timed reminders keep their minutes and have no all day offset`() {
        val lines = ReminderLine.of(listOf(Reminder(60), Reminder(5)), allDay = false)
        assertEquals(listOf(5, 60), lines.map { it.minutesBefore })
        assertTrue(lines.all { it.allDay == null })
    }

    private val series = Event(
        EventId(3),
        CalendarId(1),
        "Standup",
        EventTime.Timed(
            Instant.parse("2026-10-01T07:00:00Z"),
            Instant.parse("2026-10-01T07:30:00Z"),
            madrid
        ),
        rrule = "FREQ=DAILY;COUNT=9"
    )

    @Test
    fun `the occurrence of a series is the instance's time in the series' zone`() {
        val instance = EventInstance(
            EventId(3),
            CalendarId(1),
            "Standup",
            EventTime.Timed(
                Instant.parse("2026-10-04T07:00:00Z"),
                Instant.parse("2026-10-04T07:30:00Z"),
                madrid
            ),
            isRecurring = true
        )
        val ref = EventRef.of(instance)
        assertFalse(ref.allDay)
        assertEquals(instance.time, ref.timeOn(series))
    }

    @Test
    fun `a single event keeps its own time`() {
        val single = series.copy(rrule = null)
        val ref = EventRef(EventId(3), 0L, 1L, allDay = false)
        assertEquals(single.time, ref.timeOn(single))
    }

    @Test
    fun `an all day occurrence is dated in UTC and lasts at least a day`() {
        val allDaySeries = series.copy(
            time = EventTime.AllDay(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-03"))
        )
        val instance = EventInstance(
            EventId(3),
            CalendarId(1),
            "Trip",
            EventTime.AllDay(LocalDate.parse("2026-10-08"), LocalDate.parse("2026-10-10")),
            isRecurring = true
        )
        val ref = EventRef.of(instance)
        assertTrue(ref.allDay)
        assertEquals(instance.time, ref.timeOn(allDaySeries))
        val collapsed = ref.copy(endMillis = ref.startMillis)
        assertEquals(
            EventTime.AllDay(LocalDate.parse("2026-10-08"), LocalDate.parse("2026-10-09")),
            collapsed.timeOn(allDaySeries)
        )
    }
}
