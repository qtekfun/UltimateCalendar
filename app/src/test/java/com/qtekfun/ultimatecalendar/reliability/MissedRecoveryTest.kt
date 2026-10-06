// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.reliability

import com.qtekfun.ultimatecalendar.domain.reminders.SnoozeOption
import com.qtekfun.ultimatecalendar.domain.reminders.Snoozes
import com.qtekfun.ultimatecalendar.notify.ReminderSettings
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** Missed reminders (RF-08): the window, the first run, and a postponed reminder shown once. */
class MissedRecoveryTest {
    private val begin = Instant.parse("2026-10-08T00:00:00Z")
    private val later = begin.plus(Duration.ofHours(72))
    private val madrid = ZoneId.of("Europe/Madrid")
    private val first = "2026-10-08T00:00:00Z"

    /** An event whose 10 minute reminder was [hours] hours before [later]. */
    private suspend fun ReminderRig.missedBy(title: String, hours: Long) {
        val reminderAt = later.minus(Duration.ofHours(hours))
        timed(title, ZonedDateTime.ofInstant(reminderAt.plusSeconds(600), madrid), 10)
    }

    @TestFactory
    fun `only the chosen window is brought back, and nothing when it is never`() = listOf(
        6 to listOf("5h"),
        24 to listOf("23h", "5h"),
        48 to listOf("47h", "23h", "5h"),
        0 to emptyList()
    ).map { (window, expected) ->
        dynamicTest("$window hours") {
            rigTest(first, "Europe/Madrid", ReminderSettings(missedWindowHours = window)) {
                missedBy("5h", 5)
                missedBy("23h", 23)
                missedBy("47h", 47)
                missedBy("60h", 60)
                // The phone slept for three days: its alarms never rang and the app was dead.
                kill()
                loseAlarms()
                jumpClock(later)

                start()
                beat()
                openApp()
                openApp()

                assertEquals(expected, shownTitles())
                assertTrue(shade.all { it.missed })
            }
        }
    }

    @Test
    fun `the first run records what is past and shows none of it`() =
        rigTest(first, "Europe/Madrid", started = false) {
            repeat(30) { missedBy("Old $it", 1L + it % 20) }
            assertEquals(false, shownLog.baseline)

            jumpClock(later)
            start()
            beat()
            openApp()

            assertEquals(emptyList<String>(), shownTitles())
            assertEquals(true, shownLog.baseline)
            assertEquals(30, shownLog.records.size)
        }

    @Test
    fun `after the first run what is missed does come back`() =
        rigTest(first, "Europe/Madrid", started = false) {
            missedBy("Before install", 3)
            jumpClock(later)
            start()
            missedBy("After install", 1)
            loseAlarms()

            openApp()

            assertEquals(listOf("After install"), shownTitles())
            assertEquals(true, shade.single().missed)
        }

    @Test
    fun `a reminder that rang on time is not brought back when the app opens, whenever that is`() =
        rigTest(first, "Europe/Madrid") {
            timed("Call", ZonedDateTime.ofInstant(begin.plus(Duration.ofHours(10)), madrid), 10)
            advanceTo(begin.plus(Duration.ofHours(11)))
            repeat(3) { beat() }
            jumpClock(later)
            openApp()
            beat()

            assertEquals(listOf("Call"), shownTitles())
            assertEquals(false, shade.single().missed)
        }

    @Test
    fun `a reminder shown with a window of a day is not repeated if the window grows to two`() =
        rigTest(first, "Europe/Madrid") {
            timed("Call", ZonedDateTime.ofInstant(begin.plus(Duration.ofHours(10)), madrid), 10)
            kill()
            loseAlarms()
            jumpClock(begin.plus(Duration.ofHours(20)))
            start()
            openApp()
            assertEquals(listOf("Call"), shownTitles())

            settings.state.value = ReminderSettings(missedWindowHours = 48)
            jumpClock(begin.plus(Duration.ofHours(30)))
            openApp()
            beat()

            assertEquals(listOf("Call"), shownTitles())
        }

    private suspend fun ReminderRig.postponedCall(minutes: SnoozeOption) {
        timed("Call", ZonedDateTime.parse("2026-10-08T14:00:00+02:00[Europe/Madrid]"), 10)
        advanceTo(Instant.parse("2026-10-08T11:50:00Z"))
        snooze(shade.single().reminder, minutes)
    }

    @Test
    fun `a postponed reminder whose alarm was delivered twice shows once`() =
        rigTest(first, "Europe/Madrid") {
            postponedCall(SnoozeOption.FIVE_MINUTES)
            val postponed = alarms.single { Snoozes.isSnooze(it.id) }

            advanceTo(Instant.parse("2026-10-08T12:00:00Z"))
            redeliver(postponed)
            beat()

            assertEquals(listOf("Call", "Call"), shownTitles())
        }

    @Test
    fun `a postponed reminder that the recovery showed is not shown again by its alarm`() =
        rigTest(first, "Europe/Madrid") {
            postponedCall(SnoozeOption.FIVE_MINUTES)
            val postponed = alarms.single { Snoozes.isSnooze(it.id) }
            // Doze held the alarm back: the app opens first and the recovery shows it.
            jumpClock(Instant.parse("2026-10-08T12:00:00Z"))
            openApp()
            assertEquals(listOf("Call", "Call"), shownTitles())
            assertEquals(true, shade.last().missed)

            redeliver(postponed)
            beat()

            assertEquals(2, shade.size)
        }

    @Test
    fun `postponing again replaces the earlier postponement, so only one shows`() =
        rigTest(first, "Europe/Madrid") {
            postponedCall(SnoozeOption.FIVE_MINUTES)
            snooze(shade.first().reminder, SnoozeOption.ONE_HOUR)
            assertEquals(1, snoozed.items.size)

            advanceTo(Instant.parse("2026-10-08T12:30:00Z"))
            assertEquals(1, shade.size)
            advanceTo(Instant.parse("2026-10-08T13:00:00Z"))
            beat()

            assertEquals(2, shade.size)
            assertEquals(Instant.parse("2026-10-08T12:50:00Z"), shade.last().clock)
        }
}
