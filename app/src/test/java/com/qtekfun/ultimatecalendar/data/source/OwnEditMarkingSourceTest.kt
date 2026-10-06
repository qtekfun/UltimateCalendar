// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.data.invitations.OwnEditMarks
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class OwnEditMarkingSourceTest {
    private class RecordingMarks : OwnEditMarks {
        val calls = mutableListOf<Pair<Long, Boolean>>()

        override suspend fun mark(eventId: EventId, marked: Boolean) {
            calls += eventId.value to marked
        }
    }

    private val marks = RecordingMarks()
    private val calendar = CalendarInfo(
        CalendarId(1),
        CalendarAccount("a", "t"),
        "c",
        0,
        CalendarAccess.OWNER,
        ownerEmail = "me@example.com"
    )
    private val fake = FakeCalendarSource(listOf(calendar))
    private val source = OwnEditMarkingSource(fake, marks)
    private val start = Instant.parse("2026-06-10T10:00:00Z")

    private fun draft() = EventDraft(
        CalendarId(1),
        "Lunch",
        EventTime.Timed(start, start.plusSeconds(3600), ZoneOffset.UTC)
    )

    private suspend fun create() = (source.create(draft()) as CalendarResult.Success).value

    @Test
    fun `reads and creations are not marked`() = runTest {
        val id = create()

        source.event(id)
        source.instances(TimeRange(start, start.plusSeconds(86_400)))

        assertEquals(emptyList<Pair<Long, Boolean>>(), marks.calls)
    }

    @Test
    fun `an update, a delete and a change to one occurrence mark the event before writing`() =
        runTest {
            val id = create()
            val event = (source.event(id) as CalendarResult.Success).value

            source.update(event.copy(title = "Brunch"))
            source.editInstance(id, start, draft().copy(title = "Once"))
            source.cancelInstance(id, start)
            source.delete(id)

            assertEquals(List(4) { id.value }, marks.calls.filter { it.second }.map { it.first })
        }

    @Test
    fun `a failed write takes the mark back and the result is passed on`() = runTest {
        val result = source.delete(EventId(404))

        assertInstanceOf(CalendarResult.Failure::class.java, result)
        assertEquals(listOf(404L to true, 404L to false), marks.calls)
    }
}
