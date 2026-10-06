// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.sync.CheckFixtures
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class InvitationResponsesTest {
    private val source = FakeCalendarSource(listOf(CheckFixtures.calendar(1)))

    private suspend fun invited(attendee: String = CheckFixtures.ME): InvitationKey {
        val start = Instant.parse("2026-06-11T10:00:00Z")
        val draft = EventDraft(
            calendarId = CalendarId(1),
            title = "Lunch",
            time = EventTime.Timed(start, start.plusSeconds(3600), ZoneOffset.UTC),
            attendees = listOf(Attendee.of(attendee))
        )
        val id = (source.create(draft) as CalendarResult.Success).value
        return InvitationKey(CalendarId(1), id)
    }

    private suspend fun statusOf(key: InvitationKey) =
        (source.event(key.eventId) as CalendarResult.Success).value.attendees.single().status

    @Test
    fun `answering stores the status in the source`() = runTest {
        val key = invited()
        val outcome = SourceInvitationResponses(source).respond(key, AttendeeStatus.TENTATIVE)
        assertEquals(ResponseOutcome.Answered, outcome)
        assertEquals(AttendeeStatus.TENTATIVE, statusOf(key))
    }

    @Test
    fun `answering twice is the same as answering once`() = runTest {
        val key = invited()
        val responses = SourceInvitationResponses(source)
        responses.respond(key, AttendeeStatus.ACCEPTED)
        assertEquals(ResponseOutcome.Answered, responses.respond(key, AttendeeStatus.ACCEPTED))
        assertEquals(AttendeeStatus.ACCEPTED, statusOf(key))
    }

    @Test
    fun `an answer can be taken back`() = runTest {
        val key = invited()
        val responses = SourceInvitationResponses(source)
        responses.respond(key, AttendeeStatus.DECLINED)
        responses.respond(key, AttendeeStatus.NEEDS_ACTION)
        assertEquals(AttendeeStatus.NEEDS_ACTION, statusOf(key))
    }

    @Test
    fun `a deleted event is gone, not a failure`() = runTest {
        val key = invited()
        source.delete(key.eventId)
        val outcome = SourceInvitationResponses(source).respond(key, AttendeeStatus.ACCEPTED)
        assertEquals(ResponseOutcome.Gone, outcome)
    }

    @Test
    fun `an event the user is not invited to fails and stores nothing`() = runTest {
        val key = invited(attendee = "someone@example.com")
        val outcome = SourceInvitationResponses(source).respond(key, AttendeeStatus.ACCEPTED)
        val failed = assertInstanceOf(ResponseOutcome.Failed::class.java, outcome)
        assertInstanceOf(CalendarError.Invalid::class.java, failed.error)
        assertEquals(AttendeeStatus.NEEDS_ACTION, statusOf(key))
    }

    @Test
    fun `a permission revoked during the write is a failure, not a crash`() = runTest {
        val revoked = object : CalendarSource by source {
            override suspend fun respond(
                id: EventId,
                status: AttendeeStatus
            ): CalendarResult<Unit> = throw SecurityException("revoked")
        }
        val outcome = SourceInvitationResponses(revoked)
            .respond(InvitationKey(CalendarId(1), EventId(1)), AttendeeStatus.ACCEPTED)
        assertEquals(ResponseOutcome.Failed(CalendarError.PermissionDenied), outcome)
    }
}
