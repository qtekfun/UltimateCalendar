// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.reliability

import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.reminders.SnoozeOption
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Events edited, moved or cancelled after their reminders were planned or shown (RF-07, RF-08). */
class EventChangesTest {
    private val start = "2026-10-08T10:00:00Z"
    private val madrid = "Europe/Madrid"
    private val zone = ZoneId.of(madrid)
    private val call = ZonedDateTime.parse("2026-10-08T14:00:00+02:00[Europe/Madrid]")

    private suspend fun ReminderRig.stored(id: EventId): Event =
        (source.event(id) as CalendarResult.Success).value

    private fun Event.at(time: ZonedDateTime) = copy(
        time = EventTime.Timed(time.toInstant(), time.toInstant().plusSeconds(3_600), zone)
    )

    @Test
    fun `an event moved later moves its alarm, and the old time stays quiet`() =
        rigTest(start, madrid) {
            val id = timed("Call", call, 10)
            assertEquals(listOf(Instant.parse("2026-10-08T11:50:00Z")), alarmTimes())

            source.update(stored(id).at(call.plusHours(3)))
            assertEquals(listOf(Instant.parse("2026-10-08T14:50:00Z")), alarmTimes())

            advanceTo(Instant.parse("2026-10-08T20:00:00Z"))
            beat()
            openApp()

            assertEquals(1, shade.size)
            assertEquals(Instant.parse("2026-10-08T14:50:00Z"), shade.single().clock)
            assertEquals(false, shade.single().missed)
        }

    @Test
    fun `an event moved earlier but still ahead rings at the new time only`() =
        rigTest(start, madrid) {
            val id = timed("Call", call.plusHours(5), 10)
            source.update(stored(id).at(call))
            assertEquals(listOf(Instant.parse("2026-10-08T11:50:00Z")), alarmTimes())

            advanceTo(Instant.parse("2026-10-08T20:00:00Z"))
            beat()

            assertEquals(listOf(Instant.parse("2026-10-08T11:50:00Z")), shade.map { it.clock })
        }

    @Test
    fun `a renamed event rings with its new title and the old one never shows`() =
        rigTest(start, madrid) {
            val id = timed("Call", call, 10)
            source.update(stored(id).copy(title = "Call with Ana", location = "Room 2"))

            assertEquals(listOf("Call with Ana"), alarms.map { it.title })
            advanceTo(Instant.parse("2026-10-08T12:30:00Z"))
            beat()

            assertEquals(listOf("Call with Ana"), shownTitles())
            assertEquals("Room 2", shade.single().reminder.location)
        }

    @Test
    fun `changing the reminders of an event replaces its alarms`() = rigTest(start, madrid) {
        val id = timed("Call", call, 10)
        source.update(stored(id).copy(reminders = listOf(Reminder(30), Reminder(60))))
        assertEquals(
            listOf(Instant.parse("2026-10-08T11:00:00Z"), Instant.parse("2026-10-08T11:30:00Z")),
            alarmTimes()
        )

        source.update(stored(id).copy(reminders = emptyList()))
        assertEquals(emptyList<Instant>(), alarmTimes())

        advanceTo(Instant.parse("2026-10-08T13:00:00Z"))
        beat()
        openApp()
        assertEquals(emptyList<String>(), shownTitles())
    }

    @Test
    fun `an event cancelled before its reminder shows nothing, now or later`() =
        rigTest(start, madrid) {
            val id = timed("Call", call, 10)
            source.delete(id)
            assertEquals(emptyList<Instant>(), alarmTimes())

            advanceTo(Instant.parse("2026-10-08T13:00:00Z"))
            beat()
            openApp()

            assertEquals(emptyList<String>(), shownTitles())
        }

    @Test
    fun `an event cancelled after its reminder showed does not show it again`() =
        rigTest(start, madrid) {
            val id = timed("Call", call, 10)
            advanceTo(Instant.parse("2026-10-08T11:55:00Z"))
            source.delete(id)
            beat()
            openApp()

            assertEquals(listOf("Call"), shownTitles())
        }

    @Test
    fun `an event moved after its reminder showed reminds again at the new time`() =
        rigTest(start, madrid) {
            val id = timed("Call", call, 10)
            advanceTo(Instant.parse("2026-10-08T11:55:00Z"))
            source.update(stored(id).at(call.plusHours(3)))
            advanceTo(Instant.parse("2026-10-08T20:00:00Z"))
            beat()
            openApp()

            assertEquals(
                listOf(
                    Instant.parse("2026-10-08T11:50:00Z"),
                    Instant.parse("2026-10-08T14:50:00Z")
                ),
                shade.map { it.clock }
            )
            assertEquals(listOf(false, false), shade.map { it.missed })
        }

    @Test
    fun `a moved all-day event moves its reminder to the day before the new day`() =
        rigTest(start, madrid) {
            val id = allDay("Holiday", LocalDate.parse("2026-10-12"), 1_440)
            assertEquals(listOf(Instant.parse("2026-10-11T07:00:00Z")), alarmTimes())

            source.update(
                stored(
                    id
                ).copy(
                    time = EventTime.AllDay(
                        LocalDate.parse("2026-10-15"),
                        LocalDate.parse("2026-10-16")
                    )
                )
            )

            assertEquals(listOf(Instant.parse("2026-10-14T07:00:00Z")), alarmTimes())
        }

    @Test
    fun `one occurrence of a series cancelled or moved changes only its own reminder`() =
        rigTest(start, madrid) {
            val standup = ZonedDateTime.parse("2026-10-09T09:00:00+02:00[Europe/Madrid]")
            val id = timed("Standup", standup, 15, rrule = "FREQ=DAILY;COUNT=3")
            assertEquals(
                listOf(
                    Instant.parse("2026-10-09T06:45:00Z"),
                    Instant.parse("2026-10-10T06:45:00Z"),
                    Instant.parse("2026-10-11T06:45:00Z")
                ),
                alarmTimes()
            )

            source.cancelInstance(id, standup.plusDays(1).toInstant())
            val moved = standup.plusDays(2).plusHours(2)
            source.editInstance(
                id,
                standup.plusDays(2).toInstant(),
                EventDraft(
                    calendarId = stored(id).calendarId,
                    title = "Standup (late)",
                    time = EventTime.Timed(
                        moved.toInstant(),
                        moved.toInstant().plusSeconds(900),
                        zone
                    ),
                    // The editor sends the occurrence with all its fields, reminders included.
                    reminders = listOf(Reminder(15))
                )
            )

            assertEquals(
                listOf(
                    Instant.parse("2026-10-09T06:45:00Z"),
                    Instant.parse("2026-10-11T08:45:00Z")
                ),
                alarmTimes()
            )
            assertEquals(listOf("Standup", "Standup (late)"), alarms.map { it.title })
        }

    @Test
    fun `a postponed reminder of an event that was cancelled does not ring`() =
        rigTest(start, madrid) {
            val id = timed("Call", call, 10)
            advanceTo(Instant.parse("2026-10-08T11:50:00Z"))
            snooze(shade.single().reminder, SnoozeOption.ONE_HOUR)
            assertEquals(listOf(Instant.parse("2026-10-08T12:50:00Z")), alarmTimes())

            source.delete(id)
            assertEquals(emptyList<Instant>(), alarmTimes())
            advanceTo(Instant.parse("2026-10-08T14:00:00Z"))
            beat()
            openApp()

            assertEquals(1, shade.size)
            assertEquals(emptyList<Any>(), snoozed.items)
        }

    @Test
    fun `a postponed reminder of an event that was moved gives way to the new reminder`() =
        rigTest(start, madrid) {
            val id = timed("Call", call, 10)
            advanceTo(Instant.parse("2026-10-08T11:50:00Z"))
            snooze(shade.single().reminder, SnoozeOption.ONE_HOUR)

            source.update(stored(id).at(call.plusHours(5)))
            assertEquals(listOf(Instant.parse("2026-10-08T16:50:00Z")), alarmTimes())

            advanceTo(Instant.parse("2026-10-08T20:00:00Z"))
            beat()
            openApp()

            assertEquals(
                listOf(
                    Instant.parse("2026-10-08T11:50:00Z"),
                    Instant.parse("2026-10-08T16:50:00Z")
                ),
                shade.map { it.clock }
            )
            assertEquals(emptyList<Any>(), snoozed.items)
        }

    @Test
    fun `a postponed reminder of an event that was renamed rings with the new name`() =
        rigTest(start, madrid) {
            val id = timed("Call", call, 10)
            advanceTo(Instant.parse("2026-10-08T11:50:00Z"))
            snooze(shade.single().reminder, SnoozeOption.ONE_HOUR)

            source.update(stored(id).copy(title = "Call with Ana"))
            advanceTo(Instant.parse("2026-10-08T13:00:00Z"))

            assertEquals(listOf("Call", "Call with Ana"), shownTitles())
        }
}
