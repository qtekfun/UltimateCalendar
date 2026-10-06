// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.reliability

import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The whole chain of RF-07 and RF-08 with nothing stubbed but the Android edges: an event stored
 * in the calendar source ends up as an alarm at the right instant, through the app's reminder
 * source, the real coordinator and the real planner; and the alarm follows the event.
 */
class ReminderEndToEndTest {
    private val start = "2026-10-08T10:00:00Z"
    private val madrid = "Europe/Madrid"
    private val call = ZonedDateTime.parse("2026-10-08T14:00:00+02:00[Europe/Madrid]")

    @Test
    fun `an event with a 10 minute reminder is scheduled, moved and cancelled with the event`() =
        rigTest(start, madrid) {
            val id = timed("Call", call, 10)
            assertEquals(listOf(Instant.parse("2026-10-08T11:50:00Z")), alarmTimes())
            assertEquals(listOf("Call"), alarms.map { it.title })

            val stored = (source.event(id) as CalendarResult.Success).value
            val later = call.plusHours(2).toInstant()
            source.update(
                stored.copy(time = EventTime.Timed(later, later.plusSeconds(3_600), call.zone))
            )
            assertEquals(listOf(Instant.parse("2026-10-08T13:50:00Z")), alarmTimes())

            source.delete(id)
            assertEquals(emptyList<Instant>(), alarmTimes())
        }

    @Test
    fun `only alerts are scheduled, and a declined event is not`() = rigTest(start, madrid) {
        val mail = EventDraft(
            calendarId = CalendarId(1),
            title = "Mailed",
            time = EventTime.Timed(
                call.toInstant(),
                call.toInstant().plusSeconds(3_600),
                call.zone
            ),
            reminders = listOf(Reminder(30, ReminderMethod.EMAIL), Reminder(20))
        )
        source.create(mail)
        val invited = source.create(
            mail.copy(title = "Invited", attendees = listOf(Attendee.of("me@example.com")))
        ) as CalendarResult.Success
        assertEquals(listOf("Mailed", "Invited"), alarms.map { it.title })
        assertEquals(List(2) { Instant.parse("2026-10-08T11:40:00Z") }, alarmTimes())

        source.respond(invited.value, AttendeeStatus.DECLINED)

        assertEquals(listOf("Mailed"), alarms.map { it.title })
    }

    @Test
    fun `the alarms are there after the app was closed and the phone restarted`() =
        rigTest(start, madrid) {
            timed("Call", call, 10)

            kill()
            assertEquals(listOf(Instant.parse("2026-10-08T11:50:00Z")), alarmTimes())

            reboot()
            assertEquals(listOf(Instant.parse("2026-10-08T11:50:00Z")), alarmTimes())

            advanceTo(Instant.parse("2026-10-08T12:00:00Z"))
            assertEquals(listOf("Call"), shownTitles())
        }
}
