// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.invitations

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderPlanner
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Which re-reminders exist, which ring and which go (T40), over many days, zones and options. */
class ReRemindPlannerTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val nine = LocalTime.of(9, 0)
    private val now = Instant.parse("2026-10-20T08:00:00Z")

    private fun context(
        option: ReRemindOption = ReRemindOption.BOTH,
        at: Instant = now,
        zone: ZoneId = madrid,
        window: Duration = Duration.ofHours(24)
    ) = ReRemindContext(option, nine, zone, at, window)

    private fun timed(id: Long, start: String): Invitation {
        val begins = ZonedDateTime.parse(start)
        return Invitation(
            InvitationKey(CalendarId(1), EventId(id)),
            "Event $id",
            EventTime.Timed(begins.toInstant(), begins.toInstant().plusSeconds(3_600), begins.zone)
        )
    }

    private fun allDay(id: Long, date: String) = Invitation(
        InvitationKey(CalendarId(1), EventId(id)),
        "Day $id",
        EventTime.AllDay(LocalDate.parse(date), LocalDate.parse(date).plusDays(1))
    )

    private fun entry(
        invitation: Invitation,
        moment: ReRemindMoment,
        at: String,
        settled: Boolean
    ) = ReRemindEntry(ReRemindKey.of(invitation, moment), Instant.parse(at), settled)

    /** The log after [plan] is applied, as Room would hold it. */
    private fun apply(stored: List<ReRemindEntry>, plan: ReRemindPlan): List<ReRemindEntry> =
        stored.filter { it.key !in plan.delete && it.key !in plan.save.map { s -> s.key } } +
            plan.save

    // The moments themselves.

    @Test
    fun `the options map to their moments`() {
        assertEquals(emptyList<ReRemindMoment>(), ReRemindOption.OFF.moments)
        assertEquals(listOf(ReRemindMoment.DAY_BEFORE), ReRemindOption.DAY_BEFORE.moments)
        assertEquals(listOf(ReRemindMoment.HOUR_BEFORE), ReRemindOption.HOUR_BEFORE.moments)
        assertEquals(
            listOf(ReRemindMoment.DAY_BEFORE, ReRemindMoment.HOUR_BEFORE),
            ReRemindOption.BOTH.moments
        )
    }

    @Test
    fun `a timed event is reminded an hour before, exactly`() {
        val time = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]").time

        assertEquals(
            Instant.parse("2026-10-26T08:00:00Z"),
            ReRemindMoment.HOUR_BEFORE.fireAt(time, nine, madrid)
        )
    }

    @Test
    fun `a day before keeps the wall clock of the event across the autumn change`() {
        // Clocks go back on 25 October at 3:00; 10:00 that day is CET (09:00Z), and 10:00 the
        // day before is still CEST (08:00Z), 25 hours earlier rather than 24.
        val time = timed(1, "2026-10-25T10:00:00+01:00[Europe/Madrid]").time

        assertEquals(
            Instant.parse("2026-10-24T08:00:00Z"),
            ReRemindMoment.DAY_BEFORE.fireAt(time, nine, madrid)
        )
    }

    @Test
    fun `a day before keeps the wall clock of the event across the spring change`() {
        // Clocks go forward on 29 March at 2:00; 12:00 that day is CEST (10:00Z), and 12:00 the
        // day before is CET (11:00Z), 23 hours earlier.
        val time = timed(1, "2026-03-29T12:00:00+02:00[Europe/Madrid]").time

        assertEquals(
            Instant.parse("2026-03-28T11:00:00Z"),
            ReRemindMoment.DAY_BEFORE.fireAt(time, nine, madrid)
        )
    }

    @Test
    fun `a day before follows the zone of the event, not the phone's`() {
        val time = timed(1, "2026-10-26T10:00:00-04:00[America/New_York]").time

        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals(
            Instant.parse("2026-10-25T14:00:00Z"),
            ReRemindMoment.DAY_BEFORE.fireAt(time, nine, tokyo)
        )
    }

    @Test
    fun `an all-day event reminds at the all-day time of the day before, and of its own day`() {
        val time = allDay(1, "2026-10-26").time

        // 25 October after 3:00 is already CET (08:00Z), as is the 26th.
        assertEquals(
            Instant.parse("2026-10-25T08:00:00Z"),
            ReRemindMoment.DAY_BEFORE.fireAt(time, nine, madrid)
        )
        assertEquals(
            Instant.parse("2026-10-26T08:00:00Z"),
            ReRemindMoment.HOUR_BEFORE.fireAt(time, nine, madrid)
        )
    }

    @Test
    fun `an all-day moment is 9 in the phone's zone, wherever that is`() {
        val time = allDay(1, "2026-10-26").time
        val kolkata = ZoneId.of("Asia/Kolkata")

        assertEquals(
            Instant.parse("2026-10-25T03:30:00Z"),
            ReRemindMoment.DAY_BEFORE.fireAt(time, nine, kolkata)
        )
        assertEquals(
            Instant.parse("2026-10-26T03:30:00Z"),
            ReRemindMoment.HOUR_BEFORE.fireAt(time, nine, kolkata)
        )
    }

    @Test
    fun `an all-day event across the autumn change is still 9 local`() {
        // 25 October is 25 hours long in Madrid: the day before 9:00 is CEST, the day itself CET.
        val time = allDay(1, "2026-10-25").time

        assertEquals(
            Instant.parse("2026-10-24T07:00:00Z"),
            ReRemindMoment.DAY_BEFORE.fireAt(time, nine, madrid)
        )
        assertEquals(
            Instant.parse("2026-10-25T08:00:00Z"),
            ReRemindMoment.HOUR_BEFORE.fireAt(time, nine, madrid)
        )
    }

    @Test
    fun `keys tell occurrences, moments and events apart`() {
        val lunch = timed(7, "2026-10-26T10:00:00+01:00[Europe/Madrid]")
        val holiday = allDay(8, "2026-10-26")

        val key = ReRemindKey.of(lunch, ReRemindMoment.DAY_BEFORE)
        assertEquals(Instant.parse("2026-10-26T09:00:00Z").toEpochMilli(), key.start)
        assertEquals("1/7/DAY_BEFORE/${key.start}", key.tag)
        assertEquals(
            LocalDate.parse("2026-10-26").toEpochDay(),
            ReRemindKey.of(holiday, ReRemindMoment.HOUR_BEFORE).start
        )
        assertTrue(key != ReRemindKey.of(lunch, ReRemindMoment.HOUR_BEFORE))
        assertTrue(
            key !=
                ReRemindKey.of(
                    timed(7, "2026-10-27T10:00:00+01:00[Europe/Madrid]"),
                    ReRemindMoment.DAY_BEFORE
                )
        )
    }

    // The plan.

    @Test
    fun `off plans nothing and forgets everything that was planned`() {
        val lunch = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")
        val stored = ReRemindOption.BOTH.moments.map {
            ReRemindEntry(ReRemindKey.of(lunch, it), Instant.parse("2026-10-25T10:00:00Z"), false)
        }

        val plan = ReRemindPlanner.plan(listOf(lunch), stored, context(ReRemindOption.OFF))

        assertEquals(emptyList<ReRemindEntry>(), plan.save)
        assertEquals(stored.map { it.key }.toSet(), plan.delete.toSet())
        assertEquals(emptyList<ReRemindEntry>(), plan.alarms)
        assertEquals(emptyList<Invitation>(), plan.due)
    }

    @Test
    fun `no invitations plan nothing`() {
        val plan = ReRemindPlanner.plan(emptyList(), emptyList(), context())

        assertEquals(ReRemindPlan(emptyList(), emptyList(), emptyList(), emptyList()), plan)
    }

    @Test
    fun `a future invitation gets an alarm for each moment`() {
        val lunch = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")

        val plan = ReRemindPlanner.plan(listOf(lunch), emptyList(), context())

        val day = entry(lunch, ReRemindMoment.DAY_BEFORE, "2026-10-25T09:00:00Z", false)
        val hour = entry(lunch, ReRemindMoment.HOUR_BEFORE, "2026-10-26T08:00:00Z", false)
        assertEquals(listOf(day, hour), plan.save)
        assertEquals(listOf(day, hour), plan.alarms)
        assertEquals(emptyList<Invitation>(), plan.due)
        assertEquals(emptyList<ReRemindKey>(), plan.delete)
    }

    @Test
    fun `only the chosen moment is planned`() {
        val lunch = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")

        val day = ReRemindPlanner.plan(
            listOf(lunch),
            emptyList(),
            context(ReRemindOption.DAY_BEFORE)
        )
        val hour = ReRemindPlanner.plan(
            listOf(lunch),
            emptyList(),
            context(ReRemindOption.HOUR_BEFORE)
        )

        assertEquals(listOf(ReRemindMoment.DAY_BEFORE), day.alarms.map { it.key.moment })
        assertEquals(listOf(ReRemindMoment.HOUR_BEFORE), hour.alarms.map { it.key.moment })
    }

    @Test
    fun `the first run settles what is already past without showing it`() {
        // The event is in half an hour: both moments are behind us. Nothing floods.
        val soon = timed(1, "2026-10-20T10:30:00+02:00[Europe/Madrid]")
        // This one is tomorrow at 9: the day before is past, the hour before is not.
        val tomorrow = timed(2, "2026-10-21T09:00:00+02:00[Europe/Madrid]")

        val plan = ReRemindPlanner.plan(listOf(soon, tomorrow), emptyList(), context())

        assertEquals(emptyList<Invitation>(), plan.due)
        assertEquals(
            listOf(
                true to ReRemindMoment.DAY_BEFORE,
                true to ReRemindMoment.HOUR_BEFORE,
                true to ReRemindMoment.DAY_BEFORE,
                false to ReRemindMoment.HOUR_BEFORE
            ),
            plan.save.map { it.settled to it.key.moment }
        )
        assertEquals(
            listOf(entry(tomorrow, ReRemindMoment.HOUR_BEFORE, "2026-10-21T06:00:00Z", false)),
            plan.alarms
        )
    }

    @Test
    fun `a moment that is exactly now is settled on the first look`() {
        val lunch = timed(1, "2026-10-20T11:00:00+02:00[Europe/Madrid]")

        val plan = ReRemindPlanner.plan(
            listOf(lunch),
            emptyList(),
            context(ReRemindOption.HOUR_BEFORE)
        )

        assertEquals(listOf(true), plan.save.map { it.settled })
        assertEquals(emptyList<Invitation>(), plan.due)
    }

    @Test
    fun `planning again with the plan applied changes nothing`() {
        val lunch = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")
        val holiday = allDay(2, "2026-10-27")
        val first = ReRemindPlanner.plan(listOf(lunch, holiday), emptyList(), context())
        val stored = apply(emptyList(), first)

        val second = ReRemindPlanner.plan(listOf(lunch, holiday), stored, context())

        assertEquals(emptyList<ReRemindEntry>(), second.save)
        assertEquals(emptyList<ReRemindKey>(), second.delete)
        assertEquals(first.alarms, second.alarms)
        assertEquals(emptyList<Invitation>(), second.due)
    }

    @Test
    fun `a moment that went by within the window shows once and is then settled`() {
        val lunch = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")
        val stored = apply(emptyList(), ReRemindPlanner.plan(listOf(lunch), emptyList(), context()))
        // The app was dead from the evening before until the next morning, 6 hours after the
        // "day before" moment at 09:00Z.
        val back = Instant.parse("2026-10-25T15:00:00Z")

        val plan = ReRemindPlanner.plan(listOf(lunch), stored, context(at = back))

        assertEquals(listOf(lunch), plan.due)
        assertEquals(
            listOf(entry(lunch, ReRemindMoment.DAY_BEFORE, "2026-10-25T09:00:00Z", true)),
            plan.save
        )
        assertEquals(
            listOf(ReRemindMoment.HOUR_BEFORE),
            plan.alarms.map { it.key.moment }
        )
        val again = ReRemindPlanner.plan(listOf(lunch), apply(stored, plan), context(at = back))
        assertEquals(emptyList<Invitation>(), again.due)
        assertEquals(emptyList<ReRemindEntry>(), again.save)
    }

    @Test
    fun `what shows after a gap depends on the window`() {
        val lunch = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")
        val stored = apply(emptyList(), ReRemindPlanner.plan(listOf(lunch), emptyList(), context()))
        val back = Instant.parse("2026-10-26T07:00:00Z")

        val plan = ReRemindPlanner.plan(
            listOf(lunch),
            stored,
            context(at = back, window = Duration.ofHours(24))
        )

        // 22 hours: inside 24, so it shows; with 6 it would not.
        assertEquals(listOf(lunch), plan.due)
        val short = ReRemindPlanner.plan(
            listOf(lunch),
            stored,
            context(at = back, window = Duration.ofHours(6))
        )
        assertEquals(emptyList<Invitation>(), short.due)
        assertEquals(
            listOf(true),
            short.save.map { it.settled }
        )
    }

    @Test
    fun `a window of zero still lets an alarm that rang a little late show`() {
        val lunch = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")
        val stored = apply(emptyList(), ReRemindPlanner.plan(listOf(lunch), emptyList(), context()))
        val day = Instant.parse("2026-10-25T09:00:00Z")

        val late = ReRemindPlanner.plan(
            listOf(lunch),
            stored,
            context(at = day.plusSeconds(120), window = Duration.ZERO)
        )
        val tooLate = ReRemindPlanner.plan(
            listOf(lunch),
            stored,
            context(at = day.plus(Duration.ofMinutes(10)), window = Duration.ZERO)
        )

        assertEquals(listOf(lunch), late.due)
        assertEquals(emptyList<Invitation>(), tooLate.due)
    }

    @Test
    fun `an invitation with both moments missed shows once`() {
        val lunch = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")
        val stored = apply(emptyList(), ReRemindPlanner.plan(listOf(lunch), emptyList(), context()))
        val back = Instant.parse("2026-10-26T08:30:00Z")

        val plan = ReRemindPlanner.plan(
            listOf(lunch),
            stored,
            context(at = back, window = Duration.ofHours(48))
        )

        assertEquals(listOf(lunch), plan.due)
        assertEquals(listOf(true, true), plan.save.map { it.settled })
        assertEquals(emptyList<ReRemindEntry>(), plan.alarms)
    }

    @Test
    fun `what is due comes soonest event first, one notification each`() {
        val later = timed(1, "2026-10-26T12:00:00+01:00[Europe/Madrid]")
        val sooner = timed(2, "2026-10-26T10:00:00+01:00[Europe/Madrid]")
        val stored = apply(
            emptyList(),
            ReRemindPlanner.plan(
                listOf(later, sooner),
                emptyList(),
                context(ReRemindOption.DAY_BEFORE)
            )
        )
        val back = Instant.parse("2026-10-25T12:00:00Z")

        val plan = ReRemindPlanner.plan(
            listOf(later, sooner),
            stored,
            context(ReRemindOption.DAY_BEFORE, back)
        )

        assertEquals(listOf(sooner, later), plan.due)
    }

    @Test
    fun `a reminder that showed is not shown again when the clock goes back`() {
        val lunch = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")
        val shown = listOf(
            entry(lunch, ReRemindMoment.DAY_BEFORE, "2026-10-25T09:00:00Z", true),
            entry(lunch, ReRemindMoment.HOUR_BEFORE, "2026-10-26T08:00:00Z", false)
        )

        val plan = ReRemindPlanner.plan(
            listOf(lunch),
            shown,
            context(at = Instant.parse("2026-10-24T00:00:00Z"))
        )

        assertEquals(emptyList<ReRemindEntry>(), plan.save)
        assertEquals(emptyList<Invitation>(), plan.due)
        assertEquals(listOf(shown[1]), plan.alarms)
    }

    @Test
    fun `answered, cancelled or past invitations lose their reminders`() {
        val answered = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")
        val kept = timed(2, "2026-10-27T10:00:00+01:00[Europe/Madrid]")
        val started = timed(3, "2026-10-20T09:00:00+02:00[Europe/Madrid]")
        val stored = listOf(answered, kept, started).flatMap { invitation ->
            ReRemindOption.BOTH.moments.map {
                ReRemindEntry(
                    ReRemindKey.of(invitation, it),
                    Instant.parse("2026-10-25T09:00:00Z"),
                    false
                )
            }
        }

        // Only "kept" is still pending; "started" is pending but its event began an hour ago.
        val plan = ReRemindPlanner.plan(listOf(kept, started), stored, context())

        assertEquals(
            setOf(answered.key, started.key),
            plan.delete.map { it.invitation }.toSet()
        )
        assertEquals(4, plan.delete.size)
        assertEquals(listOf(kept.key), plan.alarms.map { it.key.invitation }.distinct())
    }

    @Test
    fun `a moved event loses the old reminders and gets new ones`() {
        val before = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")
        val after = timed(1, "2026-10-28T16:00:00+01:00[Europe/Madrid]")
        val stored =
            apply(emptyList(), ReRemindPlanner.plan(listOf(before), emptyList(), context()))

        val plan = ReRemindPlanner.plan(listOf(after), stored, context())

        assertEquals(stored.map { it.key }.toSet(), plan.delete.toSet())
        assertEquals(
            listOf(
                entry(after, ReRemindMoment.DAY_BEFORE, "2026-10-27T15:00:00Z", false),
                entry(after, ReRemindMoment.HOUR_BEFORE, "2026-10-28T14:00:00Z", false)
            ),
            plan.alarms
        )
        assertEquals(plan.alarms, plan.save)
    }

    @Test
    fun `a moved event whose new date is too close settles its new moments quietly`() {
        val before = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")
        val after = timed(1, "2026-10-20T20:00:00+02:00[Europe/Madrid]")
        val stored =
            apply(emptyList(), ReRemindPlanner.plan(listOf(before), emptyList(), context()))

        val plan = ReRemindPlanner.plan(listOf(after), stored, context())

        assertEquals(emptyList<Invitation>(), plan.due)
        assertEquals(listOf(true, false), plan.save.map { it.settled })
    }

    @Test
    fun `switching from both to one forgets the other, and back plans it quietly if past`() {
        val lunch = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")
        val both = apply(emptyList(), ReRemindPlanner.plan(listOf(lunch), emptyList(), context()))

        val dayOnly = ReRemindPlanner.plan(listOf(lunch), both, context(ReRemindOption.DAY_BEFORE))
        assertEquals(listOf(ReRemindMoment.HOUR_BEFORE), dayOnly.delete.map { it.moment })
        assertEquals(emptyList<ReRemindEntry>(), dayOnly.save)

        // Later, with the day-before moment past, the user picks both: no flood.
        val later = Instant.parse("2026-10-25T12:00:00Z")
        val afterDay = apply(both, dayOnly)
        val backToBoth = ReRemindPlanner.plan(
            listOf(lunch),
            afterDay,
            context(ReRemindOption.BOTH, later)
        )
        // The day-before row was never settled in this setup, so it shows (it is within the
        // window); a fresh switch-on (no row) would not.
        assertEquals(listOf(lunch), backToBoth.due)
        val freshSwitchOn = ReRemindPlanner.plan(
            listOf(lunch),
            emptyList(),
            context(ReRemindOption.BOTH, later)
        )
        assertEquals(emptyList<Invitation>(), freshSwitchOn.due)
        assertEquals(listOf(ReRemindMoment.HOUR_BEFORE), freshSwitchOn.alarms.map { it.key.moment })
    }

    @Test
    fun `an all-day reminder that showed stays settled when the phone changes zone`() {
        val holiday = allDay(1, "2026-10-26")
        val stored =
            apply(emptyList(), ReRemindPlanner.plan(listOf(holiday), emptyList(), context()))
        // 9:00 of 25 October in Madrid has rung. The phone lands in New York, where it is 03:00 of
        // the 25th: in that zone the moment is still 6 hours away, but it already showed.
        val rung = stored.map {
            if (it.key.moment ==
                ReRemindMoment.DAY_BEFORE
            ) {
                it.copy(settled = true)
            } else {
                it
            }
        }
        val newYork = ZoneId.of("America/New_York")
        val at = Instant.parse("2026-10-25T07:30:00Z")

        val plan = ReRemindPlanner.plan(listOf(holiday), rung, context(zone = newYork, at = at))

        assertEquals(emptyList<Invitation>(), plan.due)
        assertEquals(listOf(ReRemindMoment.HOUR_BEFORE), plan.alarms.map { it.key.moment })
    }

    @Test
    fun `an all-day reminder still to come moves to 9 in the new zone`() {
        val holiday = allDay(1, "2026-10-26")
        val stored =
            apply(emptyList(), ReRemindPlanner.plan(listOf(holiday), emptyList(), context()))
        val newYork = ZoneId.of("America/New_York")

        val plan = ReRemindPlanner.plan(listOf(holiday), stored, context(zone = newYork))

        assertEquals(
            listOf(
                entry(holiday, ReRemindMoment.DAY_BEFORE, "2026-10-25T13:00:00Z", false),
                entry(holiday, ReRemindMoment.HOUR_BEFORE, "2026-10-26T13:00:00Z", false)
            ),
            plan.save
        )
        assertEquals(plan.save, plan.alarms)
    }

    @Test
    fun `an all-day event that started in the phone's zone counts as past`() {
        val holiday = allDay(1, "2026-10-20")

        // Midnight of 20 October in Madrid was 22:00Z the day before.
        val plan = ReRemindPlanner.plan(listOf(holiday), emptyList(), context())

        assertEquals(ReRemindPlan(emptyList(), emptyList(), emptyList(), emptyList()), plan)
    }

    @Test
    fun `the same invitation twice in the list plans once`() {
        val lunch = timed(1, "2026-10-26T10:00:00+01:00[Europe/Madrid]")

        val plan = ReRemindPlanner.plan(listOf(lunch, lunch), emptyList(), context())

        assertEquals(2, plan.save.size)
        assertEquals(2, plan.alarms.size)
    }

    @Test
    fun `at most 50 alarms are set, the soonest first, and none beyond the horizon`() {
        val invitations = (1L..30L).map {
            timed(it, "2026-10-${21 + it % 4}T10:00:00+02:00[Europe/Madrid]")
        }
        val faraway = timed(99, "2026-12-26T10:00:00+01:00[Europe/Madrid]")

        val plan = ReRemindPlanner.plan(invitations + faraway, emptyList(), context())

        assertEquals(ReRemindPlanner.MAX_ALARMS, plan.alarms.size)
        assertEquals(plan.alarms.sortedBy { it.at }, plan.alarms)
        assertTrue(plan.alarms.none { it.key.invitation.eventId.value == 99L })
        // The far ones are remembered, for when they come into the horizon.
        assertEquals(62, plan.save.size)
        val horizon = now.plus(ReminderPlanner.HORIZON)
        assertTrue(plan.alarms.all { !it.at.isAfter(horizon) })
        // Alarms at the same instant come in a fixed order.
        val sameTime = plan.alarms.filter { it.at == plan.alarms.first().at }.map { it.key.tag }
        assertEquals(sameTime.sorted(), sameTime)
    }

    @Test
    fun `a moment just inside the horizon is set and just outside it is not`() {
        val inside = timed(1, "2026-11-19T09:00:00Z[UTC]")
        val outside = timed(2, "2026-11-19T09:00:01Z[UTC]")

        // Hour before: 08:00:00Z on 19 November is exactly 30 days after now.
        val plan = ReRemindPlanner.plan(
            listOf(inside, outside),
            emptyList(),
            context(ReRemindOption.HOUR_BEFORE, zone = ZoneId.of("UTC"))
        )

        assertEquals(listOf(inside.key), plan.alarms.map { it.key.invitation })
        assertEquals(2, plan.save.size)
    }
}
