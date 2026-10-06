// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.reliability

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** The phone's time zone or clock changes: TIMEZONE_CHANGED and TIME_SET plan everything again. */
class ClockAndZoneChangeTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val start = "2026-10-08T10:00:00Z"

    /** The all-day reminder of 10 October "a day before at 9:00" in each zone, by hand. */
    @TestFactory
    fun `an all-day reminder moves to 9 in the new zone`() = listOf(
        "Europe/Madrid" to "2026-10-09T07:00:00Z",
        "America/New_York" to "2026-10-09T13:00:00Z",
        "Asia/Kolkata" to "2026-10-09T03:30:00Z",
        "Australia/Lord_Howe" to "2026-10-08T22:00:00Z"
    ).map { (zone, expected) ->
        dynamicTest(zone) {
            rigTest(start, "Europe/Madrid") {
                allDay("Holiday", LocalDate.parse("2026-10-10"), 1_440)
                changeZone(ZoneId.of(zone))
                assertEquals(listOf(Instant.parse(expected)), alarmTimes())
            }
        }
    }

    @Test
    fun `a timed reminder keeps its instant when the zone changes`() =
        rigTest(start, "Europe/Madrid") {
            timed("Call", ZonedDateTime.parse("2026-10-09T10:00:00+02:00[Europe/Madrid]"), 10, 60)
            val before = alarms
            changeZone(ZoneId.of("Asia/Kolkata"))
            assertEquals(
                listOf(
                    Instant.parse("2026-10-09T07:00:00Z"),
                    Instant.parse("2026-10-09T07:50:00Z")
                ),
                alarmTimes()
            )
            assertEquals(before, alarms)
        }

    @Test
    fun `the zone changes while the app is dead and the next start plans in the new zone`() =
        rigTest(start, "Europe/Madrid") {
            allDay("Holiday", LocalDate.parse("2026-10-10"), 1_440)
            kill()
            // The alarm is still set, in the old zone, while nobody was running.
            assertEquals(listOf(Instant.parse("2026-10-09T07:00:00Z")), alarmTimes())

            zone = ZoneId.of("America/New_York")
            start()

            assertEquals(listOf(Instant.parse("2026-10-09T13:00:00Z")), alarmTimes())
        }

    @Test
    fun `after a zone change the reminder rings once, at 9 in the new zone`() =
        rigTest(start, "Europe/Madrid") {
            allDay("Holiday", LocalDate.parse("2026-10-10"), 1_440)
            changeZone(ZoneId.of("America/New_York"))

            advanceTo(Instant.parse("2026-10-09T12:59:00Z"))
            assertEquals(emptyList<String>(), shownTitles())
            advanceTo(Instant.parse("2026-10-10T00:00:00Z"))
            beat()

            assertEquals(listOf("Holiday"), shownTitles())
            assertEquals(Instant.parse("2026-10-09T13:00:00Z"), shade.single().clock)
            assertEquals(false, shade.single().missed)
        }

    @Test
    fun `a timed reminder that showed is not shown again after a zone change`() =
        rigTest(start, "Europe/Madrid") {
            timed("Call", ZonedDateTime.parse("2026-10-08T14:00:00+02:00[Europe/Madrid]"), 10)
            advanceTo(Instant.parse("2026-10-08T12:00:00Z"))
            changeZone(ZoneId.of("Asia/Kolkata"))
            beat()
            openApp()

            assertEquals(listOf("Call"), shownTitles())
            assertEquals(emptyList<Instant>(), alarmTimes())
        }

    @Test
    fun `an all-day reminder that showed is not repeated when the phone moves east`() =
        rigTest(start, "Europe/Madrid") {
            allDay("Holiday", LocalDate.parse("2026-10-10"), 1_440)
            // 9:00 in Madrid is 07:00Z; it showed.
            advanceTo(Instant.parse("2026-10-09T07:05:00Z"))
            assertEquals(listOf("Holiday"), shownTitles())

            // The phone lands in Kolkata, where 9:00 of that day was at 03:30Z, already past.
            changeZone(ZoneId.of("Asia/Kolkata"))
            beat()
            openApp()

            assertEquals(listOf("Holiday"), shownTitles())
        }

    @Test
    fun `an all-day reminder that showed does not ring again after the phone moves west`() =
        rigTest(start, "Europe/Madrid") {
            allDay("Holiday", LocalDate.parse("2026-10-10"), 1_440)
            advanceTo(Instant.parse("2026-10-09T07:05:00Z"))
            assertEquals(listOf("Holiday"), shownTitles())

            // In New York 9:00 of that day is still to come (13:00Z).
            changeZone(ZoneId.of("America/New_York"))
            advanceTo(Instant.parse("2026-10-09T14:00:00Z"))

            assertEquals(listOf("Holiday"), shownTitles())
        }

    @Test
    fun `the clock jumps forward past a reminder that rang on time, which shows once`() =
        rigTest(start, "Europe/Madrid") {
            timed("Call", ZonedDateTime.parse("2026-10-08T13:00:00+02:00[Europe/Madrid]"), 10)
            jumpClock(Instant.parse("2026-10-08T12:00:00Z"))
            // The system fires every alarm that is now past as soon as the clock is set...
            advanceTo(clock.now)
            // ...and only then the broadcast arrives.
            refresh()
            beat()
            openApp()

            assertEquals(listOf("Call"), shownTitles())
            assertEquals(false, shade.single().missed)
        }

    @Test
    fun `the clock jumps forward, the alarm is dropped, the beat brings it back once`() =
        rigTest(start, "Europe/Madrid") {
            timed("Call", ZonedDateTime.parse("2026-10-08T13:00:00+02:00[Europe/Madrid]"), 10)
            beat()
            jumpClock(Instant.parse("2026-10-08T12:00:00Z"))
            refresh()
            assertEquals(emptyList<Instant>(), alarmTimes())
            assertEquals(emptyList<String>(), shownTitles())

            beat()
            beat()

            assertEquals(listOf("Call"), shownTitles())
            assertEquals(true, shade.single().missed)
        }

    @Test
    fun `the clock set back after a reminder showed does not ring it again`() =
        rigTest(start, "Europe/Madrid") {
            timed("Call", ZonedDateTime.parse("2026-10-08T14:00:00+02:00[Europe/Madrid]"), 10)
            advanceTo(Instant.parse("2026-10-08T11:50:00Z"))
            assertEquals(listOf("Call"), shownTitles())

            jumpClock(Instant.parse("2026-10-08T11:48:00Z"))
            refresh()
            assertEquals(emptyList<Instant>(), alarmTimes())
            advanceTo(Instant.parse("2026-10-08T12:30:00Z"))
            beat()

            assertEquals(listOf("Call"), shownTitles())
        }

    @Test
    fun `the clock jumps back and reminders that are in the future again are set again`() =
        rigTest(start, "Europe/Madrid") {
            timed("Call", ZonedDateTime.parse("2026-10-08T14:00:00+02:00[Europe/Madrid]"), 10)
            jumpClock(Instant.parse("2026-10-08T11:00:00Z"))
            refresh()
            assertEquals(listOf(Instant.parse("2026-10-08T11:50:00Z")), alarmTimes())

            // The user was wrong and sets it back a day: the reminder is still in the future.
            jumpClock(Instant.parse("2026-10-07T11:00:00Z"))
            refresh()
            assertEquals(listOf(Instant.parse("2026-10-08T11:50:00Z")), alarmTimes())
            advanceTo(Instant.parse("2026-10-08T12:00:00Z"))
            assertEquals(listOf("Call"), shownTitles())
        }
}
