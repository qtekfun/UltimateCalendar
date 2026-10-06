// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

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
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceRules
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.recurrence.Until
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class EventMoverTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val first = Instant.parse("2026-10-05T07:00:00Z")
    private val calendar = CalendarInfo(
        CalendarId(1),
        CalendarAccount("me@example.com", "com.example"),
        "Mine",
        0xFF112233.toInt(),
        CalendarAccess.OWNER,
        ownerEmail = "me@example.com"
    )

    private fun timed(day: Long, minutes: Long = 0) = EventTime.Timed(
        first.plusSeconds(day * 86_400 + minutes * 60),
        first.plusSeconds(day * 86_400 + (minutes + 30) * 60),
        zone
    )

    private fun draft(rrule: String? = null) =
        EventDraft(calendar.id, "Standup", timed(0), rrule = rrule)

    private fun mover(source: CalendarSource) = EventMover(source) { zone }

    private fun starts(source: CalendarSource): List<Instant> = runBlocking {
        val range = TimeRange(first.minusSeconds(86_400), first.plusSeconds(30L * 86_400))
        source.instances(range).getOrNull().orEmpty().map { it.time.startIn(ZoneOffset.UTC) }
    }

    private fun instances(source: CalendarSource): List<EventInstance> = runBlocking {
        val range = TimeRange(first.minusSeconds(86_400), first.plusSeconds(30L * 86_400))
        source.instances(range).getOrNull().orEmpty()
    }

    private fun seeded(rrule: String? = null): Pair<FakeCalendarSource, EventId> = runBlocking {
        val source = FakeCalendarSource(listOf(calendar))
        source to requireNotNull(source.create(draft(rrule)).getOrNull())
    }

    private fun instanceOf(source: CalendarSource, index: Int) = instances(source)[index]

    // --- an event that does not repeat ---------------------------------------------------------

    @Test
    fun `an event is moved and moved back`() = runBlocking {
        val (source, id) = seeded()
        val moved = timed(0, 90)

        val result = mover(source).move(instanceOf(source, 0), moved, null)

        assertEquals(moved, source.event(id).getOrNull()?.time)
        val undo = requireNotNull(result.getOrNull())
        assertEquals(CalendarResult.Success(Unit), mover(source).undo(undo))
        assertEquals(timed(0), source.event(id).getOrNull()?.time)
    }

    @Test
    fun `an all day event is moved by days`() = runBlocking {
        val source = FakeCalendarSource(listOf(calendar))
        val day = LocalDate.of(2026, 10, 5)
        val id = requireNotNull(
            source.create(draft().copy(time = EventTime.AllDay(day, day.plusDays(1)))).getOrNull()
        )
        val instance = instances(source).single()
        val moved = EventTime.AllDay(day.plusDays(2), day.plusDays(3))

        mover(source).move(instance, moved, null)

        assertEquals(moved, source.event(id).getOrNull()?.time)
    }

    @Test
    fun `the scope is ignored for an event that does not repeat`() = runBlocking {
        val (source, id) = seeded()

        val result = mover(source).move(instanceOf(source, 0), timed(0, 60), RecurrenceScope.THIS)

        assertEquals(timed(0, 60), source.event(id).getOrNull()?.time)
        assertInstanceOf(MoveUndo.Restore::class.java, result.getOrNull())
    }

    @Test
    fun `an occurrence stored on its own is changed as an event`() = runBlocking {
        val (source, id) = seeded()
        // The provider reports such an occurrence as repeating although its own row does not.
        val instance = instanceOf(source, 0).copy(isRecurring = true)

        mover(source).move(instance, timed(0, 60), RecurrenceScope.ALL)

        assertEquals(timed(0, 60), source.event(id).getOrNull()?.time)
    }

    // --- a series ------------------------------------------------------------------------------

    @Test
    fun `only this occurrence is moved and moving it back restores its time`() = runBlocking {
        val (source, _) = seeded("FREQ=DAILY;COUNT=5")
        val original = starts(source)
        val third = instanceOf(source, 2)

        val result = mover(source).move(third, timed(2, 60), RecurrenceScope.THIS)

        assertEquals(
            original.mapIndexed { i, start -> if (i == 2) start.plusSeconds(3600) else start },
            starts(source)
        )
        val undo = requireNotNull(result.getOrNull())
        assertInstanceOf(MoveUndo.RestoreOccurrence::class.java, undo)
        assertEquals(CalendarResult.Success(Unit), mover(source).undo(undo))
        assertEquals(original, starts(source))
    }

    @Test
    fun `all occurrences move together and move back`() = runBlocking {
        val (source, id) = seeded("FREQ=DAILY;COUNT=5")
        val original = starts(source)

        val result = mover(source).move(instanceOf(source, 2), timed(2, 60), RecurrenceScope.ALL)

        assertEquals(original.map { it.plusSeconds(3600) }, starts(source))
        assertEquals(timed(0, 60), source.event(id).getOrNull()?.time)
        mover(source).undo(requireNotNull(result.getOrNull()))
        assertEquals(original, starts(source))
    }

    @Test
    fun `moving a series to another day moves all by that many days`() = runBlocking {
        val (source, _) = seeded("FREQ=DAILY;COUNT=3")
        val original = starts(source)

        mover(source).move(instanceOf(source, 1), timed(2), RecurrenceScope.ALL)

        assertEquals(original.map { it.plusSeconds(86_400) }, starts(source))
    }

    @Test
    fun `this and the following start a new series that keeps the rest of the count`() =
        runBlocking {
            val master = draft("FREQ=DAILY;COUNT=5").toEvent(EventId(7), "me@example.com")
            val source = mockk<CalendarSource>()
            val cut = slot<Event>()
            val created = slot<EventDraft>()
            coEvery { source.event(EventId(7)) } returns CalendarResult.Success(master)
            coEvery { source.update(capture(cut)) } returns CalendarResult.Success(Unit)
            coEvery { source.create(capture(created)) } returns CalendarResult.Success(EventId(8))
            val third = EventInstance(
                EventId(7),
                calendar.id,
                "Standup",
                timed(2),
                isRecurring = true
            )

            val result = mover(source).move(
                third,
                timed(2, 60),
                RecurrenceScope.THIS_AND_FOLLOWING
            )

            val rule = RecurrenceRules.parse(requireNotNull(cut.captured.rrule))
            assertEquals(Until.Moment(timed(2).start.minusSeconds(1)), rule?.until)
            assertEquals(timed(2, 60), created.captured.time)
            // Five places, two before this one: three left.
            assertEquals("FREQ=DAILY;COUNT=3", created.captured.rrule)
            assertEquals(MoveUndo.Unsplit(master, EventId(8)), result.getOrNull())
        }

    @Test
    fun `a split is taken back by deleting the new series and restoring the old one`() =
        runBlocking {
            val master = draft("FREQ=DAILY;COUNT=5").toEvent(EventId(7), "me@example.com")
            val source = mockk<CalendarSource>()
            coEvery { source.delete(any()) } returns CalendarResult.Success(Unit)
            coEvery { source.update(any()) } returns CalendarResult.Success(Unit)

            val result = mover(source).undo(MoveUndo.Unsplit(master, EventId(8)))

            assertEquals(CalendarResult.Success(Unit), result)
            coVerifyOrder {
                source.delete(EventId(8))
                source.update(master)
            }
        }

    @Test
    fun `a split that cannot be taken back leaves the old series alone`() = runBlocking {
        val master = draft("FREQ=DAILY;COUNT=5").toEvent(EventId(7), "me@example.com")
        val source = mockk<CalendarSource>()
        val failure = CalendarResult.Failure(CalendarError.SourceFailure("down"))
        coEvery { source.delete(any()) } returns failure

        val result = mover(source).undo(MoveUndo.Unsplit(master, EventId(8)))

        assertEquals(failure, result)
        coVerify(exactly = 0) { source.update(any()) }
    }

    @Test
    fun `a new series that cannot be started puts the old one back`() = runBlocking {
        val master = draft("FREQ=DAILY;COUNT=5").toEvent(EventId(7), "me@example.com")
        val source = mockk<CalendarSource>()
        val failure = CalendarResult.Failure(CalendarError.SourceFailure("down"))
        coEvery { source.event(EventId(7)) } returns CalendarResult.Success(master)
        coEvery { source.update(any()) } returns CalendarResult.Success(Unit)
        coEvery { source.create(any()) } returns failure
        val third = EventInstance(EventId(7), calendar.id, "Standup", timed(2), isRecurring = true)

        val result = mover(source).move(third, timed(2, 60), RecurrenceScope.THIS_AND_FOLLOWING)

        assertEquals(failure, result)
        coVerifyOrder {
            source.update(any())
            source.create(any())
            source.update(master)
        }
    }

    @Test
    fun `this and the following on the first occurrence moves the whole series`() = runBlocking {
        val (source, _) = seeded("FREQ=DAILY;COUNT=3")
        val original = starts(source)

        mover(source).move(
            instanceOf(source, 0),
            timed(0, 60),
            RecurrenceScope.THIS_AND_FOLLOWING
        )

        assertEquals(original.map { it.plusSeconds(3600) }, starts(source))
    }

    @Test
    fun `a repeating event cannot be moved without saying which occurrences`() = runBlocking {
        val (source, id) = seeded("FREQ=DAILY;COUNT=5")
        val original = starts(source)

        val result = mover(source).move(instanceOf(source, 2), timed(2, 60), null)

        assertInstanceOf(
            CalendarError.Invalid::class.java,
            (result as CalendarResult.Failure).error
        )
        assertEquals(original, starts(source))
        assertEquals(timed(0), source.event(id).getOrNull()?.time)
    }

    // --- failures --------------------------------------------------------------------------------

    @Test
    fun `an event that is gone cannot be moved`() = runBlocking {
        val (source, _) = seeded()
        val ghost = instanceOf(source, 0).copy(eventId = EventId(99))

        val result = mover(source).move(ghost, timed(0, 60), null)

        assertEquals(CalendarResult.Failure(CalendarError.NotFound), result)
    }

    @Test
    fun `a source that refuses leaves the failure for the caller`() = runBlocking {
        val master = draft().toEvent(EventId(7), "me@example.com")
        val source = mockk<CalendarSource>()
        val refusal = CalendarResult.Failure(CalendarError.ReadOnly)
        coEvery { source.event(EventId(7)) } returns CalendarResult.Success(master)
        coEvery { source.update(any()) } returns refusal
        val instance = EventInstance(EventId(7), calendar.id, "Standup", timed(0))

        assertEquals(refusal, mover(source).move(instance, timed(0, 60), null))
    }
}
