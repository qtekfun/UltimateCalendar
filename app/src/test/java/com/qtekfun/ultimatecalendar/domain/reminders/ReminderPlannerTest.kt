// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.reminders

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReminderPlannerTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val nine = LocalTime.of(9, 0)
    private val now = Instant.parse("2026-10-03T10:00:00Z")

    private fun timed(id: Long, start: String, vararg minutes: Int) = EventReminders(
        EventInstance(
            EventId(id),
            CalendarId(7),
            "Event $id",
            EventTime.Timed(Instant.parse(start), Instant.parse(start).plusSeconds(3_600), madrid),
            location = "Room $id"
        ),
        minutes.map { Reminder(it) }
    )

    private fun allDay(id: Long, first: String, vararg minutes: Int) = EventReminders(
        EventInstance(
            EventId(id),
            CalendarId(7),
            "Day $id",
            EventTime.AllDay(LocalDate.parse(first), LocalDate.parse(first).plusDays(1))
        ),
        minutes.map { Reminder(it) }
    )

    private fun plan(vararg events: EventReminders, at: LocalTime = nine, zone: ZoneId = madrid) =
        ReminderPlanner.plan(events.toList(), at, now, zone)

    @Test
    fun `a timed event reminds the given minutes before it starts`() {
        val reminder = plan(timed(1, "2026-10-03T16:00:00Z", 15)).single()
        assertEquals(Instant.parse("2026-10-03T15:45:00Z"), reminder.at)
        assertEquals(Instant.parse("2026-10-03T16:00:00Z"), reminder.start)
        assertEquals("Event 1", reminder.title)
        assertEquals("Room 1", reminder.location)
        assertEquals(EventId(1), reminder.eventId)
        assertEquals(CalendarId(7), reminder.calendarId)
        assertEquals(false, reminder.allDay)
    }

    @Test
    fun `an event may remind several times, earliest first, and the same minutes once`() {
        val reminders = plan(timed(1, "2026-10-04T12:00:00Z", 0, 60, 10, 60))
        assertEquals(
            listOf(
                Instant.parse("2026-10-04T11:00:00Z"),
                Instant.parse("2026-10-04T11:50:00Z"),
                Instant.parse("2026-10-04T12:00:00Z")
            ),
            reminders.map { it.at }
        )
        assertEquals(3, reminders.map { it.id }.toSet().size)
    }

    @Test
    fun `reminders of different events are merged in time order`() {
        val reminders = plan(
            timed(1, "2026-10-05T12:00:00Z", 0),
            timed(2, "2026-10-04T12:00:00Z", 0)
        )
        assertEquals(listOf(EventId(2), EventId(1)), reminders.map { it.eventId })
    }

    @Test
    fun `only alerts remind, not e-mails or texts`() {
        val event = timed(1, "2026-10-04T12:00:00Z").copy(
            reminders = listOf(
                Reminder(10, ReminderMethod.EMAIL),
                Reminder(20, ReminderMethod.SMS),
                Reminder(30, ReminderMethod.ALERT)
            )
        )
        assertEquals(
            listOf(Instant.parse("2026-10-04T11:30:00Z")),
            plan(event).map { it.at }
        )
    }

    @Test
    fun `an event without reminders plans nothing`() {
        assertEquals(emptyList<PlannedReminder>(), plan(timed(1, "2026-10-04T12:00:00Z")))
    }

    @Test
    fun `an all-day event reminds at the chosen time of its day`() {
        val reminder = plan(allDay(1, "2026-10-04", 0)).single()
        // 09:00 in Madrid is 07:00 UTC in October (CEST).
        assertEquals(Instant.parse("2026-10-04T07:00:00Z"), reminder.at)
        assertEquals(true, reminder.allDay)
        assertEquals(Instant.parse("2026-10-03T22:00:00Z"), reminder.start)
        assertEquals(
            Instant.parse("2026-10-04T08:30:00Z"),
            plan(allDay(1, "2026-10-04", 0), at = LocalTime.of(10, 30)).single().at
        )
    }

    @Test
    fun `all-day minutes before midnight count whole days, rounded up`() {
        val reminders =
            ReminderPlanner.planAll(
                listOf(allDay(1, "2026-10-10", 900, 1_440, 1_441, 10_080)),
                nine,
                madrid
            )
        assertEquals(
            listOf(
                "2026-10-03T07:00:00Z",
                "2026-10-08T07:00:00Z",
                "2026-10-09T07:00:00Z",
                "2026-10-09T07:00:00Z"
            ).map(Instant::parse),
            reminders.map { it.at }
        )
    }

    @Test
    fun `the same all-day event reminds at the local time of each zone`() {
        val event = allDay(1, "2026-10-04", 0)
        assertEquals(
            Instant.parse("2026-10-04T00:00:00Z"),
            plan(event, zone = ZoneId.of("Asia/Tokyo")).single().at
        )
        assertEquals(
            Instant.parse("2026-10-04T16:00:00Z"),
            plan(event, zone = ZoneId.of("America/Los_Angeles")).single().at
        )
    }

    @Test
    fun `a timed event reminds at the same instant whatever the phone zone`() {
        val event = timed(1, "2026-10-04T12:00:00Z", 30)
        assertEquals(
            plan(event, zone = ZoneId.of("Asia/Tokyo")).map { it.at },
            plan(event, zone = ZoneId.of("America/Los_Angeles")).map { it.at }
        )
    }

    @Test
    fun `minutes before are real time across the end of summer time`() {
        // Madrid goes back one hour on 2026-10-25: 24 h before 12:00 UTC is 12:00 UTC the day
        // before, which is 14:00 local on the 24th (CEST) for an event at 13:00 local (CET).
        val reminder = ReminderPlanner.planAll(
            listOf(timed(1, "2026-10-25T12:00:00Z", 1_440)),
            nine,
            madrid
        ).single()
        assertEquals(Instant.parse("2026-10-24T12:00:00Z"), reminder.at)
        assertEquals(
            "2026-10-24T14:00",
            reminder.at.atZone(madrid).toLocalDateTime().toString().take(16)
        )
    }

    @Test
    fun `an all-day reminder keeps its local time across the end of summer time`() {
        // The day after the change is 25 h long, yet 09:00 stays 09:00 on both sides of it.
        val before = ReminderPlanner.planAll(listOf(allDay(1, "2026-10-26", 1_440)), nine, madrid)
        val after = ReminderPlanner.planAll(listOf(allDay(1, "2026-10-26", 0)), nine, madrid)
        assertEquals(Instant.parse("2026-10-25T08:00:00Z"), before.single().at)
        assertEquals(Instant.parse("2026-10-26T08:00:00Z"), after.single().at)
    }

    @Test
    fun `an all-day time that does not exist on the day summer time starts moves forward`() {
        // 02:30 does not exist in Madrid on 2026-03-29: the clock jumps from 02:00 to 03:00.
        val reminder = ReminderPlanner.planAll(
            listOf(allDay(1, "2026-03-29", 0)),
            LocalTime.of(2, 30),
            madrid
        ).single()
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), reminder.at)
    }

    @Test
    fun `an all-day time that happens twice picks the first one the day summer time ends`() {
        // 02:30 happens twice in Madrid on 2026-10-25 (CEST, then CET): the earlier is used.
        val reminder = ReminderPlanner.planAll(
            listOf(allDay(1, "2026-10-25", 0)),
            LocalTime.of(2, 30),
            madrid
        ).single()
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), reminder.at)
    }

    @Test
    fun `past reminders are left out, and one at this very moment too`() {
        assertEquals(
            emptyList<PlannedReminder>(),
            plan(
                timed(1, "2026-10-03T09:00:00Z", 0),
                timed(2, "2026-10-03T10:30:00Z", 30)
            )
        )
    }

    @Test
    fun `planning everything keeps past reminders`() {
        val all = ReminderPlanner.planAll(
            listOf(timed(1, "2026-10-03T09:00:00Z", 60, 0), timed(2, "2026-10-04T09:00:00Z", 0)),
            nine,
            madrid
        )
        assertEquals(
            listOf("2026-10-03T08:00:00Z", "2026-10-03T09:00:00Z", "2026-10-04T09:00:00Z")
                .map(Instant::parse),
            all.map { it.at }
        )
    }

    @Test
    fun `no more alarms than the cap are planned, the soonest ones`() {
        val first = Instant.parse("2026-10-04T12:00:00Z")
        val events = (1..ReminderPlanner.MAX_ALARMS + 5).map {
            timed(it.toLong(), first.plusSeconds(it * 60L).toString(), 0)
        }
        val planned = ReminderPlanner.plan(events, nine, now, madrid)
        assertEquals(ReminderPlanner.MAX_ALARMS, planned.size)
        assertEquals(EventId(1), planned.first().eventId)
        assertEquals(EventId(ReminderPlanner.MAX_ALARMS.toLong()), planned.last().eventId)
    }

    @Test
    fun `an all-day reminder keeps its id in every zone, a timed one at another day does not`() {
        val event = allDay(1, "2026-10-10", 1_440)
        val inMadrid = plan(event, zone = madrid).single()
        val inNewYork = plan(event, zone = ZoneId.of("America/New_York")).single()
        assertNotEquals(inMadrid.at, inNewYork.at)
        assertEquals(inMadrid.id, inNewYork.id)
        assertNotEquals(inMadrid.id, plan(allDay(1, "2026-10-11", 1_440)).single().id)
        assertNotEquals(inMadrid.id, plan(allDay(1, "2026-10-10", 600)).single().id)
    }

    @Test
    fun `occurrences of a repetition and their reminders get their own ids`() {
        val first = plan(timed(1, "2026-10-04T12:00:00Z", 10)).single()
        val next = plan(timed(1, "2026-10-11T12:00:00Z", 10)).single()
        val other = plan(timed(1, "2026-10-04T12:00:00Z", 20)).single()
        assertNotEquals(first.id, next.id)
        assertNotEquals(first.id, other.id)
        assertEquals(first.id, plan(timed(1, "2026-10-04T12:00:00Z", 10)).single().id)
    }

    @Test
    fun `the video call of an event, in its place or description, goes with its reminders`() {
        val event = timed(1, "2026-10-04T12:00:00Z", 10)
        val inDescription = event.copy(description = "Join: https://meet.google.com/abc.")
        assertEquals("https://meet.google.com/abc", plan(inDescription).single().joinUrl)
        val inPlace = event.copy(instance = event.instance.copy(location = "https://zoom.us/j/1"))
        assertEquals("https://zoom.us/j/1", plan(inPlace).single().joinUrl)
        assertNull(plan(event).single().joinUrl)
    }

    @Test
    fun `a cancelled occurrence of a repetition plans nothing, the others keep reminders`() {
        val week1 = timed(1, "2026-10-04T12:00:00Z", 10)
        val week3 = timed(1, "2026-10-18T12:00:00Z", 10)
        assertEquals(
            listOf(Instant.parse("2026-10-04T11:50:00Z"), Instant.parse("2026-10-18T11:50:00Z")),
            plan(week1, week3).map { it.at }
        )
        assertEquals(listOf(Instant.parse("2026-10-18T11:50:00Z")), plan(week3).map { it.at })
    }

    @Test
    fun `a repeating all-day event reminds on each of its days`() {
        val days = listOf("2026-10-05", "2026-10-12").map { allDay(1, it, 900) }
        assertEquals(
            listOf("2026-10-04T07:00:00Z", "2026-10-11T07:00:00Z").map(Instant::parse),
            plan(*days.toTypedArray()).map { it.at }
        )
    }
}
