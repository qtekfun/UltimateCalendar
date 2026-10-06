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
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultRemindersTest {
    private val timed = EventTime.Timed(
        Instant.parse("2026-10-08T12:00:00Z"),
        Instant.parse("2026-10-08T13:00:00Z"),
        ZoneId.of("Europe/Madrid")
    )
    private val day = EventTime.AllDay(LocalDate.parse("2026-10-09"), LocalDate.parse("2026-10-10"))

    private fun event(time: EventTime, usesDefaults: Boolean, vararg own: Reminder) =
        EventReminders(
            EventInstance(EventId(1), CalendarId(1), "Event", time),
            own.toList(),
            usesDefaults = usesDefaults
        )

    @Test
    fun `a timed event that uses the defaults gets the timed ones, after its own`() {
        val resolved = DefaultReminders.resolve(
            listOf(event(timed, true, Reminder(60))),
            timed = listOf(10, 30),
            allDay = listOf(0)
        ).single()

        assertEquals(listOf(Reminder(60), Reminder(10), Reminder(30)), resolved.reminders)
        assertEquals(false, resolved.usesDefaults)
    }

    @Test
    fun `an all-day event that uses the defaults gets the all-day ones`() {
        val resolved = DefaultReminders.resolve(
            listOf(event(day, true)),
            timed = listOf(10),
            allDay = listOf(0, 1_440)
        ).single()

        assertEquals(listOf(Reminder(0), Reminder(1_440)), resolved.reminders)
    }

    @Test
    fun `a default equal to an own reminder is not repeated`() {
        val resolved = DefaultReminders.resolve(
            listOf(event(timed, true, Reminder(10))),
            timed = listOf(10),
            allDay = emptyList()
        ).single()

        assertEquals(listOf(Reminder(10)), resolved.reminders)
    }

    @Test
    fun `an event that does not use the defaults is left as it is`() {
        val own = event(timed, false, Reminder(5, ReminderMethod.EMAIL))

        assertEquals(
            listOf(own),
            DefaultReminders.resolve(listOf(own), timed = listOf(10), allDay = listOf(0))
        )
    }

    @Test
    fun `with no default chosen the event keeps only its own`() {
        val resolved = DefaultReminders.resolve(
            listOf(event(timed, true, Reminder(5))),
            timed = emptyList(),
            allDay = emptyList()
        ).single()

        assertEquals(listOf(Reminder(5)), resolved.reminders)
    }
}
