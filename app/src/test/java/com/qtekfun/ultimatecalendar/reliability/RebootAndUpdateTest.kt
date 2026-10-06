// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.reliability

import com.qtekfun.ultimatecalendar.domain.reminders.SnoozeOption
import com.qtekfun.ultimatecalendar.notify.ReminderSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** BOOT_COMPLETED and MY_PACKAGE_REPLACED: the alarms are gone, the app plans them again. */
class RebootAndUpdateTest {
    private val start = "2026-10-08T10:00:00Z"
    private val madrid = "Europe/Madrid"
    private val call = ZonedDateTime.parse("2026-10-08T14:00:00+02:00[Europe/Madrid]")

    private suspend fun ReminderRig.someEvents() {
        timed("Call", call, 10, 60)
        allDay("Holiday", LocalDate.parse("2026-10-12"), 1_440)
        val standup = ZonedDateTime.parse("2026-10-09T09:00:00+02:00[Europe/Madrid]")
        timed("Standup", standup, 15, rrule = "FREQ=DAILY;COUNT=3")
    }

    @Test
    fun `after a restart every reminder is set again, the same as before`() =
        rigTest(start, madrid) {
            someEvents()
            val before = alarms
            assertEquals(6, before.size)

            kill()
            loseAlarms()
            assertEquals(emptyList<Instant>(), alarmTimes())
            start()

            assertEquals(before, alarms)
        }

    @Test
    fun `after an update the alarms the system dropped are set again`() = rigTest(start, madrid) {
        someEvents()
        val before = alarms

        // The package was replaced: the old process is gone and so are its alarms.
        reboot()

        assertEquals(before, alarms)
    }

    @Test
    fun `a restart with the app killed beforehand and alarms kept does not duplicate any`() =
        rigTest(start, madrid) {
            someEvents()
            val before = alarms
            kill()
            start()
            start()
            assertEquals(before, alarms)
        }

    @Test
    fun `reminders ring on time with the process dead, once, and the next start adds nothing`() =
        rigTest(start, madrid) {
            timed("Call", call, 10)
            kill()

            advanceTo(Instant.parse("2026-10-08T12:30:00Z"))
            assertEquals(listOf("Call"), shownTitles())
            assertEquals(false, shade.single().missed)

            start()
            beat()
            openApp()
            assertEquals(listOf("Call"), shownTitles())
        }

    @Test
    fun `a postponed reminder is set again after a restart and rings once at its time`() =
        rigTest(start, madrid) {
            timed("Call", call, 10)
            advanceTo(Instant.parse("2026-10-08T11:50:00Z"))
            snooze(shade.single().reminder, SnoozeOption.FIFTEEN_MINUTES)
            assertEquals(listOf(Instant.parse("2026-10-08T12:05:00Z")), alarmTimes())

            jumpClock(Instant.parse("2026-10-08T11:55:00Z"))
            reboot()
            assertEquals(listOf(Instant.parse("2026-10-08T12:05:00Z")), alarmTimes())

            advanceTo(Instant.parse("2026-10-08T13:00:00Z"))
            beat()

            assertEquals(listOf("Call", "Call"), shownTitles())
            assertEquals(listOf(false, false), shade.map { it.missed })
            assertEquals(Instant.parse("2026-10-08T12:05:00Z"), shade.last().clock)
            assertEquals(emptyList<Any>(), snoozed.items)
        }

    @Test
    fun `a postponed reminder that came due while the phone was off shows once after boot`() =
        rigTest(start, madrid) {
            timed("Call", call, 10)
            advanceTo(Instant.parse("2026-10-08T11:50:00Z"))
            snooze(shade.single().reminder, SnoozeOption.FIVE_MINUTES)
            kill()
            loseAlarms()

            jumpClock(Instant.parse("2026-10-08T12:30:00Z"))
            start()
            beat()
            openApp()
            reboot()
            advanceTo(Instant.parse("2026-10-08T14:00:00Z"))

            assertEquals(listOf("Call", "Call"), shownTitles())
            assertEquals(true, shade.last().missed)
            assertTrue(snoozed.items.isEmpty())
        }

    @Test
    fun `reminders that passed while the phone was off come back once, within the window`() =
        rigTest(start, madrid) {
            timed("Call", call, 10, 60)
            timed("Old", ZonedDateTime.parse("2026-10-08T11:00:00+02:00[Europe/Madrid]"), 5)
            kill()
            loseAlarms()

            // Off from 10:00Z until 22:30Z, with a 6 hour window: "Call" (11:00Z, 11:50Z) is out.
            settings.state.value = ReminderSettings(missedWindowHours = 6)
            jumpClock(Instant.parse("2026-10-08T22:30:00Z"))
            start()
            assertEquals(emptyList<String>(), shownTitles())

            settings.state.value = ReminderSettings(missedWindowHours = 24)
            openApp()
            openApp()
            beat()

            assertEquals(listOf("Old", "Call", "Call"), shownTitles())
            assertEquals(listOf(true, true, true), shade.map { it.missed })
        }

    @Test
    fun `an event that started during the restart brings back its reminder as late`() =
        rigTest(start, madrid) {
            timed("Call", call, 10)
            kill()
            loseAlarms()
            jumpClock(Instant.parse("2026-10-08T12:20:00Z"))

            start()
            beat()

            assertEquals(listOf("Call"), shownTitles())
            assertEquals(true, shade.single().missed)
            assertEquals(Instant.parse("2026-10-08T11:50:00Z"), shade.single().reminder.at)
        }
}
