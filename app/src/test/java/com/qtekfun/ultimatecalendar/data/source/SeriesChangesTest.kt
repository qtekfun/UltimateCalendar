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
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceRules
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceSplitter
import com.qtekfun.ultimatecalendar.domain.recurrence.SeriesChange
import com.qtekfun.ultimatecalendar.domain.recurrence.Until
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeriesChangesTest {
    private val zone = ZoneId.of("UTC")
    private val first = Instant.parse("2026-10-05T09:00:00Z")
    private val calendar = CalendarInfo(
        CalendarId(1),
        CalendarAccount("me@example.com", "LOCAL"),
        "Mine",
        0xFF112233.toInt(),
        CalendarAccess.OWNER,
        ownerEmail = "me@example.com"
    )
    private val draft = EventDraft(
        calendar.id,
        "Standup",
        EventTime.Timed(first, first.plusSeconds(1800), zone),
        rrule = "FREQ=DAILY;COUNT=5"
    )

    /** The third day of a five day series, as the provider's `Instances` would give it. */
    private val third = EventTime.Timed(
        first.plusSeconds(2 * 86_400),
        first.plusSeconds(2 * 86_400 + 1800),
        zone
    )

    private fun instanceStarts(source: CalendarSource): List<Instant> = runBlocking {
        val range = TimeRange(first.minusSeconds(86_400), first.plusSeconds(30L * 86_400))
        source.instances(range).getOrNull().orEmpty().map { it.time.startIn(ZoneOffset.UTC) }
    }

    private fun seeded(): Pair<FakeCalendarSource, Event> = runBlocking {
        val source = FakeCalendarSource(listOf(calendar))
        val id = requireNotNull(source.create(draft).getOrNull())
        source to requireNotNull(source.event(id).getOrNull())
    }

    private fun change(master: Event, scope: RecurrenceScope): SeriesChange =
        requireNotNull(RecurrenceSplitter.delete(master, third, scope).getOrNull())

    @Test
    fun `deleting only this occurrence cancels it and keeps the others`() = runBlocking {
        val (source, master) = seeded()
        val result = SeriesChanges.apply(source, master, change(master, RecurrenceScope.THIS))
        assertEquals(CalendarResult.Success(Unit), result)
        assertEquals(
            listOf(0L, 1L, 3L, 4L).map { first.plusSeconds(it * 86_400) },
            instanceStarts(source)
        )
    }

    @Test
    fun `deleting all removes the whole series`() = runBlocking {
        val (source, master) = seeded()
        val result = SeriesChanges.apply(source, master, change(master, RecurrenceScope.ALL))
        assertEquals(CalendarResult.Success(Unit), result)
        assertEquals(emptyList<Instant>(), instanceStarts(source))
        assertEquals(CalendarResult.Failure(CalendarError.NotFound), source.event(master.id))
    }

    @Test
    fun `deleting this and the following ends the series before the occurrence`() = runBlocking {
        val master = draft.toEvent(EventId(7), "me@example.com")
        val source = mockk<CalendarSource>()
        val truncated = slot<Event>()
        coEvery { source.update(capture(truncated)) } returns CalendarResult.Success(Unit)
        val change = change(master, RecurrenceScope.THIS_AND_FOLLOWING)
        assertTrue(change is SeriesChange.Split)

        val result = SeriesChanges.apply(source, master, change)

        assertEquals(CalendarResult.Success(Unit), result)
        val rule = RecurrenceRules.parse(requireNotNull(truncated.captured.rrule))
        assertEquals(Until.Moment(Instant.parse("2026-10-07T08:59:59Z")), rule?.until)
        assertNull(rule?.count)
        coVerify(exactly = 0) { source.create(any()) }
    }

    @Test
    fun `an edited occurrence is stored as an exception of the series`() = runBlocking {
        val (source, master) = seeded()
        val edited = master.copy(title = "Moved standup", time = third)
        val result = SeriesChanges.apply(
            source,
            master,
            SeriesChange.ReplaceOccurrence(third, edited)
        )
        assertEquals(CalendarResult.Success(Unit), result)
        val titles = source.instances(TimeRange(first, first.plusSeconds(30L * 86_400)))
            .getOrNull().orEmpty().map { it.title }
        assertEquals(listOf("Standup", "Standup", "Moved standup", "Standup", "Standup"), titles)
    }

    @Test
    fun `an updated series replaces the stored one`() = runBlocking {
        val (source, master) = seeded()
        val result = SeriesChanges.apply(
            source,
            master,
            SeriesChange.Update(master.copy(title = "Renamed"))
        )
        assertEquals(CalendarResult.Success(Unit), result)
        assertEquals("Renamed", source.event(master.id).getOrNull()?.title)
    }

    @Test
    fun `a split cuts the old series and starts the new one`() = runBlocking {
        val master = draft.toEvent(EventId(7))
        val source = mockk<CalendarSource>()
        val cut = master.copy(rrule = "FREQ=DAILY;UNTIL=20261007T085959Z")
        val next = master.copy(id = RecurrenceSplitter.UNSAVED, title = "New series")
        coEvery { source.update(any()) } returns CalendarResult.Success(Unit)
        coEvery { source.create(any()) } returns CalendarResult.Success(EventId(8))

        val result = SeriesChanges.apply(source, master, SeriesChange.Split(cut, next))

        assertEquals(CalendarResult.Success(Unit), result)
        coVerifyOrder {
            source.update(cut)
            source.create(next.toDraft())
        }
    }

    @Test
    fun `a split that cannot cut leaves the calendar alone`() = runBlocking {
        val master = draft.toEvent(EventId(7))
        val source = mockk<CalendarSource>()
        val failure = CalendarResult.Failure(CalendarError.ReadOnly)
        coEvery { source.update(any()) } returns failure

        val result = SeriesChanges.apply(source, master, SeriesChange.Split(master, master))

        assertEquals(failure, result)
        coVerify(exactly = 0) { source.create(any()) }
    }

    @Test
    fun `a split whose new series fails puts the old one back`() = runBlocking {
        val master = draft.toEvent(EventId(7))
        val cut = master.copy(rrule = "FREQ=DAILY;UNTIL=20261007T085959Z")
        val source = mockk<CalendarSource>()
        val failure = CalendarResult.Failure(CalendarError.SourceFailure("down"))
        coEvery { source.update(any()) } returns CalendarResult.Success(Unit)
        coEvery { source.create(any()) } returns failure

        val result = SeriesChanges.apply(source, master, SeriesChange.Split(cut, master))

        assertEquals(failure, result)
        coVerifyOrder {
            source.update(cut)
            source.create(any())
            source.update(master)
        }
    }

    @Test
    fun `a source failure is passed on`() = runBlocking {
        val master = draft.toEvent(EventId(7))
        val source = mockk<CalendarSource>()
        val failure = CalendarResult.Failure(CalendarError.PermissionDenied)
        coEvery { source.delete(any()) } returns failure
        assertEquals(failure, SeriesChanges.apply(source, master, SeriesChange.Delete(master.id)))
    }

    @Test
    fun `an event becomes a draft with everything but its id and organizer`() {
        val event = draft.toEvent(EventId(3), "boss@example.com")
        assertEquals(draft, event.toDraft())
    }
}
