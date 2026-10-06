// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.reliability

import com.qtekfun.ultimatecalendar.notify.ReminderSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Daylight saving time (RF-08) in a zone with the usual hour, one with a half-hour offset and no
 * change, and Lord Howe, whose clocks move half an hour. Dates and instants are written by hand.
 */
class DaylightSavingTest {
    /** A day of a change, the length of that day and the day before it, to start at. */
    private data class Change(
        val zone: String,
        val day: String,
        val hours: Double,
        val gap: Boolean?
    )

    private val changes = listOf(
        Change("Europe/Madrid", "2026-03-29", 23.0, gap = true),
        Change("Europe/Madrid", "2026-10-25", 25.0, gap = false),
        Change("America/New_York", "2026-03-08", 23.0, gap = true),
        Change("America/New_York", "2026-11-01", 25.0, gap = false),
        Change("Australia/Lord_Howe", "2026-10-04", 23.5, gap = true),
        Change("Australia/Lord_Howe", "2026-04-05", 24.5, gap = false),
        Change("Asia/Kolkata", "2026-10-25", 24.0, gap = null)
    )

    private fun Change.name() = "$zone $day"

    private fun Change.date() = LocalDate.parse(day)

    private fun Change.length(): Double {
        val zoneId = ZoneId.of(zone)
        val start = date().atStartOfDay(zoneId).toInstant()
        val end = date().plusDays(1).atStartOfDay(zoneId).toInstant()
        return Duration.between(start, end).toMinutes() / MINUTES_PER_HOUR
    }

    private fun phoneAt(change: Change, body: suspend ReminderRig.() -> Unit) = rigTest(
        change.date().minusDays(3).atStartOfDay(ZoneId.of(change.zone)).toInstant().toString(),
        change.zone,
        body = body
    )

    @TestFactory
    fun `the days of the changes are as long as the tests assume`(): List<DynamicTest> =
        changes.map { change ->
            dynamicTest(change.name()) { assertEquals(change.hours, change.length(), 0.0) }
        }

    @TestFactory
    fun `all-day reminders ring at 9 each day, whatever the length of the day`() =
        changes.map { change ->
            dynamicTest(change.name()) {
                phoneAt(change) {
                    // Events on the day before, the day of and the day after: "a day before".
                    (0L..2L).forEach { offset ->
                        allDay("Day $offset", change.date().plusDays(offset), 1_440)
                    }
                    val nine = LocalTime.of(9, 0)
                    val expected = (-1L..1L).map {
                        ZonedDateTime.of(change.date().plusDays(it), nine, ZoneId.of(change.zone))
                            .toInstant()
                    }
                    assertEquals(expected, alarmTimes())
                    // The step that holds the change is as long as the day.
                    val step = Duration.between(expected[0], expected[1]).toMinutes() /
                        MINUTES_PER_HOUR
                    assertEquals(change.hours, step, 0.0)

                    advanceTo(expected.last().plusSeconds(60))
                    beat()

                    assertEquals(listOf("Day 0", "Day 1", "Day 2"), shownTitles())
                    assertEquals(expected, shade.map { it.clock })
                    assertEquals(listOf(false, false, false), shade.map { it.missed })
                }
            }
        }

    /** The start in the zone, with its offset, and the reminder instant that follows from it. */
    private data class TimedCase(
        val name: String,
        val zone: String,
        val start: String,
        val alarm: String
    )

    @TestFactory
    fun `a timed reminder is a real hour before, also across and inside the change`() = listOf(
        // 03:30 on the day clocks go forward: 02:30 does not exist, the alarm is at 01:30 CET.
        TimedCase(
            "Madrid gap",
            "Europe/Madrid",
            "2026-03-29T03:30:00+02:00",
            "2026-03-29T00:30:00Z"
        ),
        // 02:30 the second time (CET): an hour before is 02:30 the first time (CEST).
        TimedCase(
            "Madrid overlap",
            "Europe/Madrid",
            "2026-10-25T02:30:00+01:00",
            "2026-10-25T00:30:00Z"
        ),
        TimedCase(
            "New York gap",
            "America/New_York",
            "2026-03-08T03:30:00-04:00",
            "2026-03-08T06:30:00Z"
        ),
        TimedCase(
            "New York overlap",
            "America/New_York",
            "2026-11-01T01:30:00-05:00",
            "2026-11-01T05:30:00Z"
        ),
        TimedCase("Kolkata", "Asia/Kolkata", "2026-06-01T09:30:00+05:30", "2026-06-01T03:00:00Z"),
        TimedCase(
            "Lord Howe gap",
            "Australia/Lord_Howe",
            "2026-10-04T03:00:00+11:00",
            "2026-10-03T15:00:00Z"
        ),
        TimedCase(
            "Lord Howe overlap",
            "Australia/Lord_Howe",
            "2026-04-05T01:45:00+10:30",
            "2026-04-04T14:15:00Z"
        )
    ).map { case ->
        dynamicTest(case.name) {
            val start = ZonedDateTime.parse("${case.start}[${case.zone}]")
            rigTest(start.minusDays(2).toInstant().toString(), case.zone) {
                timed("Meeting", start, 60)
                assertEquals(listOf(Instant.parse(case.alarm)), alarmTimes())

                advanceTo(start.toInstant())
                beat()
                openApp()

                assertEquals(listOf("Meeting"), shownTitles())
                assertEquals(Instant.parse(case.alarm), shade.single().clock)
            }
        }
    }

    /** An all-day reminder set to a time the change skips or repeats. */
    private data class AllDayCase(
        val name: String,
        val zone: String,
        val time: String,
        val event: String,
        val alarm: String
    )

    @TestFactory
    fun `an all-day reminder at a time inside the change rings once`() = listOf(
        // 02:30 does not exist: it moves forward to 03:30 CEST.
        AllDayCase("Madrid gap", "Europe/Madrid", "02:30", "2026-03-30", "2026-03-29T01:30:00Z"),
        // 02:30 happens twice: the first one, CEST.
        AllDayCase(
            "Madrid overlap",
            "Europe/Madrid",
            "02:30",
            "2026-10-26",
            "2026-10-25T00:30:00Z"
        ),
        AllDayCase(
            "New York gap",
            "America/New_York",
            "02:30",
            "2026-03-09",
            "2026-03-08T07:30:00Z"
        ),
        AllDayCase(
            "New York overlap",
            "America/New_York",
            "01:30",
            "2026-11-02",
            "2026-11-01T05:30:00Z"
        ),
        // 02:15 is in the half hour that does not exist: it moves to 02:45 (+11).
        AllDayCase(
            "Lord Howe gap",
            "Australia/Lord_Howe",
            "02:15",
            "2026-10-05",
            "2026-10-03T15:45:00Z"
        ),
        AllDayCase(
            "Lord Howe overlap",
            "Australia/Lord_Howe",
            "01:45",
            "2026-04-06",
            "2026-04-04T14:45:00Z"
        )
    ).map { case ->
        dynamicTest(case.name) {
            val day = LocalDate.parse(case.event)
            val begin = day.minusDays(4).atStartOfDay(ZoneId.of(case.zone)).toInstant()
            rigTest(
                begin.toString(),
                case.zone,
                ReminderSettings(allDayTime = LocalTime.parse(case.time))
            ) {
                allDay("Birthday", day, 1_440)
                assertEquals(listOf(Instant.parse(case.alarm)), alarmTimes())

                advanceTo(Instant.parse(case.alarm).plus(Duration.ofHours(3)))
                beat()
                openApp()

                assertEquals(listOf("Birthday"), shownTitles())
                assertEquals(Instant.parse(case.alarm), shade.single().clock)
            }
        }
    }

    @TestFactory
    fun `a daily event keeps its local time and its reminders ring once across the change`() =
        changes.map { change ->
            dynamicTest(change.name()) {
                phoneAt(change) {
                    val zone = ZoneId.of(change.zone)
                    val first = ZonedDateTime.of(
                        change.date().minusDays(1),
                        LocalTime.of(9, 0),
                        zone
                    )
                    timed("Standup", first, 15, rrule = "FREQ=DAILY;COUNT=4")
                    val expected = (0L..3L).map {
                        ZonedDateTime.of(change.date().plusDays(it - 1), LocalTime.of(8, 45), zone)
                            .toInstant()
                    }
                    assertEquals(expected, alarmTimes())
                    assertEquals(
                        change.hours,
                        Duration.between(expected[0], expected[1]).toMinutes() / MINUTES_PER_HOUR,
                        0.0
                    )

                    advanceTo(expected.last().plus(Duration.ofHours(2)))
                    beat()
                    openApp()

                    assertEquals(4, shade.size)
                    assertEquals(expected, shade.map { it.clock })
                    assertTrue(shade.none { it.missed })
                }
            }
        }

    @TestFactory
    fun `the phone moves through the change with the alarms dropped and nothing is repeated`() =
        changes.map { change ->
            dynamicTest(change.name()) {
                phoneAt(change) {
                    val zone = ZoneId.of(change.zone)
                    val first = ZonedDateTime.of(
                        change.date().minusDays(1),
                        LocalTime.of(9, 0),
                        zone
                    )
                    timed("Standup", first, 15, rrule = "FREQ=DAILY;COUNT=4")
                    settings.state.value = ReminderSettings(missedWindowHours = 48)
                    // The phone is off from the day before the change to an hour after the 9:00
                    // of the change day, so two reminders pass.
                    kill()
                    loseAlarms()
                    jumpClock(first.plusDays(1).plusHours(1).toInstant())
                    start()
                    beat()
                    openApp()
                    advanceTo(first.plusDays(5).toInstant())
                    beat()

                    assertEquals(4, shade.size)
                    assertEquals(4, shade.map { it.reminder.id }.toSet().size)
                }
            }
        }

    private companion object {
        const val MINUTES_PER_HOUR = 60.0
    }
}
