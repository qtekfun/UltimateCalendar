// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnavailableCalendarSourceTest {
    private val source = UnavailableCalendarSource
    private val at = Instant.parse("2026-06-10T10:00:00Z")
    private val draft = EventDraft(CalendarId(1), "t", EventTime.Timed(at, at, ZoneOffset.UTC))

    @Test
    fun `every call fails as a source failure instead of throwing`() = runTest {
        val results = listOf(
            source.calendars(),
            source.instances(TimeRange(at, at.plusSeconds(1))),
            source.search("budget"),
            source.event(EventId(1)),
            source.create(draft),
            source.update(draft.toEvent(EventId(1))),
            source.delete(EventId(1)),
            source.editInstance(EventId(1), at, draft),
            source.cancelInstance(EventId(1), at),
            source.respond(EventId(1), AttendeeStatus.ACCEPTED)
        )

        results.forEach {
            assertEquals(
                CalendarResult.Failure(CalendarError.SourceFailure("no calendar source is bound")),
                it
            )
        }
    }
}
