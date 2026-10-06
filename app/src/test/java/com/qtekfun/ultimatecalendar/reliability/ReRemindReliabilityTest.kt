// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.reliability

import com.qtekfun.ultimatecalendar.domain.invitations.InvitationAlert
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindOption
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T40 over the real coordinator, checker, Room and the fake calendar: an unanswered invitation
 * reminds again once per moment, whatever the system does to the process, the alarms, the clock
 * or the zone. Real alarm delivery on a phone is not covered here.
 */
class ReRemindReliabilityTest {
    private val start = "2026-10-20T08:00:00Z"
    private val madrid = "Europe/Madrid"

    // 12:00 CEST on 23 October: the day before is 10:00Z on the 22nd, the hour before 09:00Z.
    private val lunch = ZonedDateTime.parse("2026-10-23T12:00:00+02:00[Europe/Madrid]")
    private val dayBefore = Instant.parse("2026-10-22T10:00:00Z")
    private val hourBefore = Instant.parse("2026-10-23T09:00:00Z")

    @Test
    fun `off by default sets no alarm and shows nothing`() =
        reRemindTest(start, madrid, ReRemindOption.OFF) {
            invite("Lunch", lunch)
            check()
            advanceTo(Instant.parse("2026-10-24T00:00:00Z"))

            assertEquals(emptyList<Instant>(), alarmTimes())
            assertEquals(emptyList<String>(), shownTitles())
        }

    @Test
    fun `each moment rings once, as an invitation notification that alerts`() =
        reRemindTest(start, madrid, ReRemindOption.BOTH) {
            invite("Lunch", lunch)
            check()
            assertEquals(listOf(dayBefore, hourBefore), alarmTimes())

            advanceTo(dayBefore.plusSeconds(60))
            assertEquals(listOf("Lunch"), shownTitles())
            assertEquals(InvitationAlert.REMINDER, shade.single().alert)
            assertEquals(listOf(hourBefore), alarmTimes())

            advanceTo(hourBefore.plusSeconds(60))
            assertEquals(listOf("Lunch", "Lunch"), shownTitles())
            assertEquals(emptyList<Instant>(), alarmTimes())

            // The event goes by: nothing more, ever.
            advanceTo(Instant.parse("2026-10-24T00:00:00Z"))
            check()
            beat()
            assertEquals(2, shade.size)
        }

    @Test
    fun `an invitation answered elsewhere before the alarm shows nothing and drops its alarms`() =
        reRemindTest(start, madrid, ReRemindOption.BOTH) {
            val id = invite("Lunch", lunch)
            check()
            answerElsewhere(id)

            advanceTo(dayBefore.plusSeconds(60))

            assertEquals(emptyList<String>(), shownTitles())
            assertEquals(emptyList<Instant>(), alarmTimes())
        }

    @Test
    fun `a cancelled event shows nothing and drops its alarms`() =
        reRemindTest(start, madrid, ReRemindOption.BOTH) {
            val id = invite("Lunch", lunch)
            check()
            source.delete(id)

            advanceTo(dayBefore.plusSeconds(60))

            assertEquals(emptyList<String>(), shownTitles())
            assertEquals(emptyList<Instant>(), alarmTimes())
        }

    @Test
    fun `a moved event gets its alarms at the new time and none at the old one`() =
        reRemindTest(start, madrid, ReRemindOption.BOTH) {
            val id = invite("Lunch", lunch)
            check()
            val moved = ZonedDateTime.parse("2026-10-24T15:00:00+02:00[Europe/Madrid]")
            source.update(
                event(id).copy(
                    time = EventTime.Timed(
                        moved.toInstant(),
                        moved.toInstant().plusSeconds(3_600),
                        moved.zone
                    )
                )
            )

            check()

            assertEquals(
                listOf(
                    Instant.parse("2026-10-23T13:00:00Z"),
                    Instant.parse("2026-10-24T12:00:00Z")
                ),
                alarmTimes()
            )
            advanceTo(Instant.parse("2026-10-23T12:00:00Z"))
            assertEquals(emptyList<String>(), shownTitles())
        }

    @Test
    fun `the answer changes with the option, and every change is applied at once`() =
        reRemindTest(start, madrid, ReRemindOption.BOTH) {
            invite("Lunch", lunch)
            check()

            choose(ReRemindOption.HOUR_BEFORE)
            assertEquals(listOf(hourBefore), alarmTimes())
            choose(ReRemindOption.DAY_BEFORE)
            assertEquals(listOf(dayBefore), alarmTimes())
            choose(ReRemindOption.OFF)
            assertEquals(emptyList<Instant>(), alarmTimes())
            choose(ReRemindOption.BOTH)
            assertEquals(listOf(dayBefore, hourBefore), alarmTimes())
            assertEquals(emptyList<String>(), shownTitles())
        }

    @Test
    fun `switching the option on never floods with moments already past`() =
        reRemindTest(start, madrid, ReRemindOption.OFF) {
            // Half an hour away: both moments are behind us. Tomorrow at 10: only the hour is not.
            invite("Soon", ZonedDateTime.parse("2026-10-20T10:30:00+02:00[Europe/Madrid]"))
            invite("Tomorrow", ZonedDateTime.parse("2026-10-21T10:00:00+02:00[Europe/Madrid]"))
            check()

            choose(ReRemindOption.BOTH)

            assertEquals(emptyList<String>(), shownTitles())
            assertEquals(listOf(Instant.parse("2026-10-21T07:00:00Z")), alarmTimes())
        }

    @Test
    fun `the first start with the option on, and nothing remembered, shows nothing`() =
        reRemindTest(start, madrid, ReRemindOption.BOTH) {
            invite("Soon", ZonedDateTime.parse("2026-10-20T10:30:00+02:00[Europe/Madrid]"))
            check()
            kill()
            start()
            beat()

            assertEquals(emptyList<String>(), shownTitles())
            assertEquals(emptyList<Instant>(), alarmTimes())
        }

    @Test
    fun `a reminder missed while the app was dead shows once on return and not again`() =
        reRemindTest(start, madrid, ReRemindOption.BOTH) {
            invite("Lunch", lunch)
            check()
            // The system killed the app and dropped its alarms; it comes back 4 hours late.
            kill()
            loseAlarms()
            jumpClock(dayBefore.plus(Duration.ofHours(4)))

            start()
            assertEquals(listOf("Lunch"), shownTitles())
            assertEquals(listOf(hourBefore), alarmTimes())

            beat()
            check()
            kill()
            start()
            reboot()
            beat()
            assertEquals(listOf("Lunch"), shownTitles())
            assertEquals(listOf(hourBefore), alarmTimes())
        }

    @Test
    fun `a reminder missed longer ago than the window is not brought back`() =
        reRemindTest(start, madrid, ReRemindOption.BOTH, missedWindowHours = 6) {
            invite("Lunch", lunch)
            check()
            kill()
            loseAlarms()
            jumpClock(dayBefore.plus(Duration.ofHours(10)))

            start()

            assertEquals(emptyList<String>(), shownTitles())
            assertEquals(listOf(hourBefore), alarmTimes())
        }

    @Test
    fun `many invitations missed together show once each, never twice`() =
        reRemindTest(start, madrid, ReRemindOption.BOTH, missedWindowHours = 48) {
            val titles = (1..12).map { "Meeting $it" }
            titles.forEachIndexed { index, title ->
                invite(
                    title,
                    ZonedDateTime.parse(
                        "2026-10-24T14:00:00+02:00[Europe/Madrid]"
                    ).plusMinutes(index * 5L)
                )
            }
            check()
            assertEquals(24, alarms.size)
            kill()
            loseAlarms()
            // Back on the evening of the 23rd: every "day before" went by, no "hour before" has.
            jumpClock(Instant.parse("2026-10-23T20:00:00Z"))

            start()
            beat()
            check()

            assertEquals(titles, shownTitles())
            assertEquals(12, alarms.size)
        }

    @Test
    fun `after a restart the alarms are the same and what showed does not show again`() =
        reRemindTest(start, madrid, ReRemindOption.BOTH) {
            invite("Lunch", lunch)
            check()
            advanceTo(dayBefore.plusSeconds(60))
            val before = alarms

            reboot()

            assertEquals(before, alarms)
            advanceTo(hourBefore.plusSeconds(60))
            assertEquals(listOf("Lunch", "Lunch"), shownTitles())
        }

    @Test
    fun `the clock going back does not repeat a reminder that showed`() =
        reRemindTest(start, madrid, ReRemindOption.BOTH) {
            invite("Lunch", lunch)
            check()
            advanceTo(dayBefore.plusSeconds(60))

            jumpClock(Instant.parse("2026-10-20T08:00:00Z"))
            refresh()
            beat()
            advanceTo(hourBefore.minusSeconds(60))

            assertEquals(listOf("Lunch"), shownTitles())
        }

    @Test
    fun `an all-day reminder follows the phone to a new zone and does not repeat after showing`() =
        reRemindTest(start, madrid, ReRemindOption.BOTH) {
            inviteAllDay("Holiday", LocalDate.parse("2026-10-26"))
            check()
            assertEquals(
                listOf(
                    Instant.parse("2026-10-25T08:00:00Z"),
                    Instant.parse("2026-10-26T08:00:00Z")
                ),
                alarmTimes()
            )

            val newYork = ZoneId.of("America/New_York")
            changeZone(newYork)
            assertEquals(
                listOf(
                    Instant.parse("2026-10-25T13:00:00Z"),
                    Instant.parse("2026-10-26T13:00:00Z")
                ),
                alarmTimes()
            )

            advanceTo(Instant.parse("2026-10-25T13:05:00Z"))
            assertEquals(listOf("Holiday"), shownTitles())

            // Back in Madrid, where that moment (08:00Z) went by hours ago: it already showed.
            changeZone(ZoneId.of(madrid))
            beat()
            check()
            assertEquals(listOf("Holiday"), shownTitles())
        }

    @Test
    fun `a timed day before keeps the wall clock across the autumn change`() =
        reRemindTest(start, madrid, ReRemindOption.DAY_BEFORE) {
            invite("Brunch", ZonedDateTime.parse("2026-10-25T10:00:00+01:00[Europe/Madrid]"))
            check()

            assertEquals(listOf(Instant.parse("2026-10-24T08:00:00Z")), alarmTimes())
        }

    @Test
    fun `an alarm that rings with the app dead starts it, checks and shows`() =
        reRemindTest(start, madrid, ReRemindOption.HOUR_BEFORE) {
            invite("Lunch", lunch)
            check()
            kill()

            advanceTo(hourBefore.plusSeconds(1))

            assertEquals(listOf("Lunch"), shownTitles())
            assertTrue(alarms.isEmpty())
        }
}
