// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.calendar

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.editor.EditTarget
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventSaverTest {
    private val utc = ZoneId.of("UTC")
    private val account = CalendarAccount("me@example.com", "com.google")
    private val calendar = CalendarInfo(
        CalendarId(1),
        account,
        "Work",
        0xFF0B63CE.toInt(),
        CalendarAccess.OWNER,
        ownerEmail = "me@example.com"
    )
    private val source = FakeCalendarSource(listOf(calendar))
    private val saver = EventSaver(source)
    private val first = Instant.parse("2026-03-10T09:00:00Z")
    private val window = TimeRange(first.minusSeconds(3600), first.plusSeconds(20 * 86_400L))

    private fun day(n: Int) = first.plusSeconds(n * 86_400L)

    private fun timed(dayIndex: Int, hourOffset: Long = 0) = EventTime.Timed(
        day(dayIndex).plusSeconds(hourOffset * 3600),
        day(dayIndex).plusSeconds(hourOffset * 3600 + 3600),
        utc
    )

    private fun draft(
        title: String,
        time: EventTime = timed(0),
        rrule: String? = null,
        calendarId: CalendarId = calendar.id
    ) = EventDraft(calendarId, title, time, rrule = rrule)

    private suspend fun store(draft: EventDraft): Event {
        val id = (source.create(draft) as CalendarResult.Success).value
        return (source.event(id) as CalendarResult.Success).value
    }

    private suspend fun instances(): List<EventInstance> =
        (source.instances(window) as CalendarResult.Success).value

    private suspend fun series(rrule: String = "FREQ=DAILY;COUNT=5"): Event =
        store(draft("Standup", rrule = rrule))

    private fun targetAt(master: Event, dayIndex: Int) = EditTarget(master, timed(dayIndex))

    @Test
    fun `a new event is created in its calendar`() = runTest {
        val result = saver.create(draft("Lunch"))

        assertEquals(CalendarResult.Success(Unit), result)
        assertEquals(listOf("Lunch"), instances().map { it.title })
    }

    @Test
    fun `a new event in a calendar that refuses it fails and stores nothing`() = runTest {
        val readOnly = calendar.copy(id = CalendarId(2), access = CalendarAccess.READ)
        source.addCalendar(readOnly)

        val result = saver.create(draft("Lunch", calendarId = readOnly.id))

        assertEquals(CalendarResult.Failure(CalendarError.ReadOnly), result)
        assertTrue(instances().isEmpty())
    }

    @Test
    fun `a single event is replaced as a whole`() = runTest {
        val single = store(draft("Lunch"))
        val edited = draft("Brunch", timed(0, hourOffset = 2))

        val result = saver.update(EditTarget(single, single.time), edited, scope = null)

        assertEquals(CalendarResult.Success(Unit), result)
        val stored = (source.event(single.id) as CalendarResult.Success).value
        assertEquals("Brunch", stored.title)
        assertEquals(timed(0, 2), stored.time)
    }

    @Test
    fun `a single event ignores the scope`() = runTest {
        val single = store(draft("Lunch"))

        val result = saver.update(
            EditTarget(single, single.time),
            draft("Brunch"),
            RecurrenceScope.THIS
        )

        assertEquals(CalendarResult.Success(Unit), result)
        assertEquals("Brunch", (source.event(single.id) as CalendarResult.Success).value.title)
    }

    @Test
    fun `a read-only calendar rejects the edit and keeps the event`() = runTest {
        val locked = FakeCalendarSource(listOf(calendar.copy(access = CalendarAccess.CONTRIBUTE)))
        val id = (locked.create(draft("Lunch")) as CalendarResult.Success).value
        val stored = (locked.event(id) as CalendarResult.Success).value

        val result = EventSaver(
            locked
        ).update(EditTarget(stored, stored.time), draft("Brunch"), null)

        assertEquals(CalendarResult.Failure(CalendarError.ReadOnly), result)
        assertEquals("Lunch", (locked.event(id) as CalendarResult.Success).value.title)
    }

    @Test
    fun `only this event changes just that occurrence`() = runTest {
        val master = series()
        val edited = draft("Retro", timed(2, hourOffset = 3))

        val result = saver.update(targetAt(master, 2), edited, RecurrenceScope.THIS)

        assertEquals(CalendarResult.Success(Unit), result)
        val titles = instances().map { it.title }
        assertEquals(listOf("Standup", "Standup", "Retro", "Standup", "Standup"), titles)
        assertEquals(timed(2, 3), instances()[2].time)
        assertEquals(
            "FREQ=DAILY;COUNT=5",
            (source.event(master.id) as CalendarResult.Success).value.rrule
        )
    }

    @Test
    fun `all events changes the series and moves it by the same shift`() = runTest {
        val master = series()
        // The third occurrence is moved a day later and renamed: the whole series follows.
        val edited = draft("Daily sync", timed(3, hourOffset = 1), rrule = "FREQ=DAILY;COUNT=5")

        val result = saver.update(targetAt(master, 2), edited, RecurrenceScope.ALL)

        assertEquals(CalendarResult.Success(Unit), result)
        val stored = (source.event(master.id) as CalendarResult.Success).value
        assertEquals("Daily sync", stored.title)
        assertEquals(timed(1, hourOffset = 1), stored.time)
        assertEquals(5, instances().size)
    }

    @Test
    fun `no scope means all events`() = runTest {
        val master = series()

        val result = saver.update(
            targetAt(master, 1),
            draft("Renamed", timed(1), "FREQ=DAILY;COUNT=5"),
            null
        )

        assertEquals(CalendarResult.Success(Unit), result)
        assertEquals(List(5) { "Renamed" }, instances().map { it.title })
    }

    @Test
    fun `this and following ends the series before and starts another from here`() = runTest {
        val master = series()
        val edited = draft("Planning", timed(2, hourOffset = 4), rrule = "FREQ=DAILY;COUNT=5")

        val result = saver.update(targetAt(master, 2), edited, RecurrenceScope.THIS_AND_FOLLOWING)

        assertEquals(CalendarResult.Success(Unit), result)
        val found = instances()
        // Two days of the old series, then the count carries over: 5 - 2 = 3 more.
        assertEquals(
            listOf("Standup", "Standup", "Planning", "Planning", "Planning"),
            found.map {
                it.title
            }
        )
        assertEquals(timed(2, 4), found[2].time)
        val old = (source.event(master.id) as CalendarResult.Success).value
        assertEquals("FREQ=DAILY;UNTIL=20260312T085959Z", old.rrule)
        assertEquals(2, found.map { it.eventId }.distinct().size)
    }

    @Test
    fun `this and following keeps an end date as it is`() = runTest {
        val master = series("FREQ=DAILY;UNTIL=20260320T000000Z")
        val edited = draft("Planning", timed(3), rrule = "FREQ=DAILY;UNTIL=20260320T000000Z")

        val result = saver.update(targetAt(master, 3), edited, RecurrenceScope.THIS_AND_FOLLOWING)

        assertEquals(CalendarResult.Success(Unit), result)
        assertEquals(
            listOf("Standup", "Standup", "Standup") + List(7) { "Planning" },
            instances().map { it.title }
        )
    }

    @Test
    fun `this and following on the first occurrence changes the whole series`() = runTest {
        val master = series()

        val result = saver.update(
            targetAt(master, 0),
            draft("All new", timed(0), "FREQ=DAILY;COUNT=5"),
            RecurrenceScope.THIS_AND_FOLLOWING
        )

        assertEquals(CalendarResult.Success(Unit), result)
        assertEquals(List(5) { "All new" }, instances().map { it.title })
        assertEquals(1, instances().map { it.eventId }.distinct().size)
    }

    @Test
    fun `a split that cannot end the old series changes nothing`() = runTest {
        val master = series()
        val failing = object : CalendarSource by source {
            override suspend fun update(event: Event): CalendarResult<Unit> =
                CalendarResult.Failure(CalendarError.SourceFailure("down"))
        }

        val result = EventSaver(failing).update(
            targetAt(master, 2),
            draft("Planning", timed(2), "FREQ=DAILY;COUNT=5"),
            RecurrenceScope.THIS_AND_FOLLOWING
        )

        assertEquals(CalendarResult.Failure(CalendarError.SourceFailure("down")), result)
        assertEquals(List(5) { "Standup" }, instances().map { it.title })
        assertEquals(1, instances().map { it.eventId }.distinct().size)
    }

    @Test
    fun `a split whose new series is refused puts the old series back`() = runTest {
        val master = series()
        val refusing = object : CalendarSource by source {
            override suspend fun create(draft: EventDraft): CalendarResult<EventId> =
                CalendarResult.Failure(CalendarError.Invalid("no"))
        }

        val result = EventSaver(refusing).update(
            targetAt(master, 2),
            draft("Planning", timed(2), "FREQ=DAILY;COUNT=5"),
            RecurrenceScope.THIS_AND_FOLLOWING
        )

        assertInstanceOf(CalendarResult.Failure::class.java, result)
        assertEquals(
            "FREQ=DAILY;COUNT=5",
            (source.event(master.id) as CalendarResult.Success).value.rrule
        )
        assertEquals(List(5) { "Standup" }, instances().map { it.title })
    }

    @Test
    fun `a series with a rule the app cannot read cannot be split`() = runTest {
        val master = series().copy(rrule = "FREQ=DAILY;BYHOUR=9")

        val result = saver.update(
            targetAt(master, 2),
            draft("Planning", timed(2), "FREQ=DAILY;BYHOUR=9"),
            RecurrenceScope.THIS_AND_FOLLOWING
        )

        assertInstanceOf(CalendarResult.Failure::class.java, result)
        assertEquals(
            "FREQ=DAILY;COUNT=5",
            (source.event(master.id) as CalendarResult.Success).value.rrule
        )
    }
}
