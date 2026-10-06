// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.reminders

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.reminders.EventReminders
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.ReminderSettings
import com.qtekfun.ultimatecalendar.reliability.FakeReminderSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Which occurrences remind, and when they are read again (RF-07, RF-08), over the fake source. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarReminderEventSourceTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-10-06T08:00:00Z")
    private val noon = Instant.parse("2026-10-06T10:00:00Z")
    private val account = CalendarAccount("tests@example.com", "LOCAL")
    private val visible = calendar(1, visible = true)
    private val hidden = calendar(2, visible = false)
    private val fake = FakeCalendarSource(listOf(visible, hidden))
    private val settings = FakeReminderSettings()

    private fun calendar(id: Long, visible: Boolean) = CalendarInfo(
        CalendarId(id),
        account,
        "Cal $id",
        0xFF0B63CE.toInt(),
        CalendarAccess.OWNER,
        visible = visible,
        ownerEmail = "me@example.com"
    )

    private fun draft(
        title: String = "Call",
        start: Instant = noon,
        calendar: Long = 1,
        rrule: String? = null,
        attendees: List<Attendee> = emptyList(),
        reminders: List<Reminder> = listOf(Reminder(10))
    ) = EventDraft(
        calendarId = CalendarId(calendar),
        title = title,
        time = EventTime.Timed(start, start.plusSeconds(HOUR), madrid),
        rrule = rrule,
        attendees = attendees,
        reminders = reminders
    )

    private suspend fun create(draft: EventDraft): EventId =
        (fake.create(draft) as CalendarResult.Success).value

    /** Collects what the source answers while [body] runs; the answers so far are `emitted`. */
    private fun observed(
        source: CalendarSource = fake,
        to: Instant = now.plusSeconds(30 * DAY),
        body: suspend TestScope.(emitted: List<List<EventReminders>>) -> Unit
    ) = runTest {
        val emitted = mutableListOf<List<EventReminders>>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        CalendarReminderEventSource(source, settings, DEBOUNCE_MS)
            .observe(now, to)
            .onEach { emitted += it }
            .launchIn(scope)
        try {
            body(emitted)
        } finally {
            scope.cancel()
        }
    }

    private fun List<EventReminders>.titles() = map { it.instance.title }

    @Test
    fun `an event with a reminder is read with its reminders, location and description`() =
        observed { emitted ->
            create(
                draft().copy(
                    location = "Room 1",
                    description = "https://meet.example.com/a",
                    reminders = listOf(Reminder(10), Reminder(60, ReminderMethod.EMAIL))
                )
            )
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()

            val found = emitted.last().single()
            assertEquals("Call", found.instance.title)
            assertEquals("Room 1", found.instance.location)
            assertEquals("https://meet.example.com/a", found.description)
            assertEquals(
                listOf(Reminder(10), Reminder(60, ReminderMethod.EMAIL)),
                found.reminders
            )
            assertEquals(CalendarId(1), found.instance.calendarId)
        }

    @Test
    fun `what is already stored is read at once, without waiting for a change`() = runTest {
        create(draft())
        val emitted = mutableListOf<List<EventReminders>>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        CalendarReminderEventSource(fake, settings, DEBOUNCE_MS)
            .observe(now, now.plusSeconds(DAY))
            .onEach { emitted += it }
            .launchIn(scope)

        assertEquals(listOf(listOf("Call")), emitted.map { it.titles() })
        scope.cancel()
    }

    @Test
    fun `a declined event does not remind, a tentative or unanswered one does`() =
        observed { emitted ->
            val me = Attendee.of("me@example.com")
            val declined = create(draft("Declined", attendees = listOf(me)))
            create(draft("Tentative", attendees = listOf(me)))
            create(draft("Waiting", attendees = listOf(me)))
            create(draft("Own"))
            fake.respond(declined, AttendeeStatus.DECLINED)
            fake.respond(EventId(2), AttendeeStatus.TENTATIVE)
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()

            assertEquals(listOf("Tentative", "Waiting", "Own"), emitted.last().titles())
        }

    @Test
    fun `declining a series silences all its occurrences`() = observed { emitted ->
        val id = create(
            draft(
                rrule = "FREQ=DAILY;COUNT=3",
                attendees = listOf(Attendee.of("me@example.com"))
            )
        )
        fake.respond(id, AttendeeStatus.DECLINED)
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()

        assertEquals(emptyList<String>(), emitted.last().titles())
    }

    @Test
    fun `events of a calendar that is not visible do not remind`() = observed { emitted ->
        create(draft("Shown", calendar = 1))
        create(draft("Hidden", calendar = 2))
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()

        assertEquals(listOf("Shown"), emitted.last().titles())
    }

    @Test
    fun `a calendar that becomes visible starts reminding, and one that is hidden stops`() =
        observed { emitted ->
            create(draft("Other", calendar = 2))
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()
            assertEquals(emptyList<String>(), emitted.last().titles())

            fake.addCalendar(hidden.copy(visible = true))
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()
            assertEquals(listOf("Other"), emitted.last().titles())

            fake.addCalendar(hidden)
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()
            assertEquals(emptyList<String>(), emitted.last().titles())
        }

    @Test
    fun `only the window asked for is read, so the horizon holds`() =
        observed(to = now.plusSeconds(30 * DAY)) { emitted ->
            create(draft("Inside", noon.plusSeconds(29 * DAY)))
            create(draft("Beyond", noon.plusSeconds(31 * DAY)))
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()

            assertEquals(listOf("Inside"), emitted.last().titles())
        }

    @Test
    fun `a series gives its occurrences, minus a cancelled one and with a moved one moved`() =
        observed { emitted ->
            val id = create(draft(rrule = "FREQ=DAILY;COUNT=4"))
            fake.cancelInstance(id, noon.plusSeconds(DAY))
            fake.editInstance(
                id,
                noon.plusSeconds(2 * DAY),
                draft(
                    "Moved",
                    noon.plusSeconds(2 * DAY + 3 * HOUR),
                    reminders = listOf(Reminder(5))
                )
            )
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()

            val found = emitted.last()
            assertEquals(listOf("Call", "Moved", "Call"), found.titles())
            assertEquals(
                listOf(
                    noon,
                    noon.plusSeconds(2 * DAY + 3 * HOUR),
                    noon.plusSeconds(3 * DAY)
                ),
                found.map { (it.instance.time as EventTime.Timed).start }
            )
            assertEquals(
                listOf(listOf(Reminder(10)), listOf(Reminder(5)), listOf(Reminder(10))),
                found.map { it.reminders }
            )
        }

    @Test
    fun `an event that uses the calendar defaults gets those of the settings, timed or all-day`() =
        observed { emitted ->
            settings.state.value = ReminderSettings(
                defaultReminders = listOf(15),
                defaultAllDayReminders = listOf(0, 1_440)
            )
            val timed = create(draft("Timed", reminders = listOf(Reminder(60))))
            val allDay = create(
                draft("Day", reminders = emptyList()).copy(
                    time = EventTime.AllDay(
                        LocalDate.parse("2026-10-08"),
                        LocalDate.parse("2026-10-09")
                    )
                )
            )
            create(draft("Own", reminders = listOf(Reminder(5))))
            fake.useDefaultReminders(timed)
            fake.useDefaultReminders(allDay)
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()

            val found = emitted.last().associateBy { it.instance.title }
            assertEquals(listOf(Reminder(60), Reminder(15)), found.getValue("Timed").reminders)
            assertEquals(listOf(Reminder(0), Reminder(1_440)), found.getValue("Day").reminders)
            assertEquals(listOf(Reminder(5)), found.getValue("Own").reminders)
            assertEquals(false, found.getValue("Timed").usesDefaults)
        }

    @Test
    fun `changing the default reminders reads again`() = observed { emitted ->
        val id = create(draft(reminders = emptyList()))
        fake.useDefaultReminders(id)
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        assertEquals(listOf(Reminder(10)), emitted.last().single().reminders)

        settings.state.value = ReminderSettings(defaultReminders = listOf(30))
        runCurrent()
        assertEquals(listOf(Reminder(30)), emitted.last().single().reminders)
    }

    @Test
    fun `an unrelated change of the settings does not read again`() = observed { emitted ->
        val before = emitted.size
        settings.state.value = ReminderSettings(robustMode = true)
        runCurrent()

        assertEquals(before, emitted.size)
    }

    @Test
    fun `an edit, a deletion and a new event are all read again`() = observed { emitted ->
        val id = create(draft())
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        assertEquals(listOf("Call"), emitted.last().titles())

        fake.update((fake.event(id) as CalendarResult.Success).value.copy(title = "Renamed"))
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        assertEquals(listOf("Renamed"), emitted.last().titles())

        fake.delete(id)
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        assertEquals(emptyList<String>(), emitted.last().titles())
    }

    @Test
    fun `a burst of changes is read once, after the last one`() = observed { emitted ->
        val before = emitted.size
        repeat(BURST) {
            create(draft("Call $it"))
            advanceTimeBy(DEBOUNCE_MS / 2)
            runCurrent()
        }
        assertEquals(before, emitted.size)

        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()

        assertEquals(before + 1, emitted.size)
        assertEquals(BURST, emitted.last().size)
    }

    @Test
    fun `a failing read repeats the last answer, so the alarms are not cancelled`() = runTest {
        val flaky = Flaky(fake)
        create(draft())
        val emitted = mutableListOf<List<EventReminders>>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        CalendarReminderEventSource(flaky, settings, DEBOUNCE_MS)
            .observe(now, now.plusSeconds(DAY))
            .onEach { emitted += it }
            .launchIn(scope)
        assertEquals(listOf("Call"), emitted.last().titles())

        flaky.failure = CalendarError.SourceFailure("busy")
        create(draft("Other"))
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()

        assertEquals(2, emitted.size)
        assertEquals(listOf("Call"), emitted.last().titles())

        flaky.failure = null
        create(draft("Third"))
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        assertEquals(listOf("Call", "Other", "Third"), emitted.last().titles().sorted())
        scope.cancel()
    }

    @Test
    fun `a failure with nothing read before is an empty answer, and so is a missing permission`() =
        runTest {
            val flaky = Flaky(fake)
            flaky.failure = CalendarError.SourceFailure("busy")
            val first = mutableListOf<List<EventReminders>>()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            CalendarReminderEventSource(flaky, settings, DEBOUNCE_MS)
                .observe(now, now.plusSeconds(DAY))
                .onEach { first += it }
                .launchIn(scope)
            assertEquals(listOf(emptyList<EventReminders>()), first)

            create(draft())
            flaky.failure = CalendarError.PermissionDenied
            fake.addCalendar(visible)
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()
            assertEquals(emptyList<EventReminders>(), first.last())

            flaky.failure = null
            flaky.failCalendars = true
            fake.addCalendar(visible)
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()
            assertEquals(emptyList<EventReminders>(), first.last())
            scope.cancel()
        }

    /** A source that fails on demand, with the answers of [inner] otherwise. */
    private class Flaky(private val inner: FakeCalendarSource) : CalendarSource by inner {
        var failure: CalendarError? = null
        var failCalendars = false

        override suspend fun calendars() = if (failCalendars) {
            CalendarResult.Failure(CalendarError.PermissionDenied)
        } else {
            inner.calendars()
        }

        override suspend fun instancesWithReminders(
            range: TimeRange,
            calendarIds: Set<CalendarId>?
        ): CalendarResult<List<EventReminders>> = failure?.let { CalendarResult.Failure(it) }
            ?: inner.instancesWithReminders(range, calendarIds)
    }

    private companion object {
        const val DEBOUNCE_MS = 100L
        const val HOUR = 3_600L
        const val DAY = 86_400L
        const val BURST = 5
    }
}
