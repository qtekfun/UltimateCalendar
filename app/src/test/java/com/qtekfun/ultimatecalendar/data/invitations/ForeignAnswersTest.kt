// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationKey
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.sync.RecordingSyncRequester
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * An invitation of account A to account B (both the user's) is answered in B's own copy of the
 * event, never in A's.
 */
class ForeignAnswersTest {
    private val a = "a@gmail.com"
    private val b = "b@gmail.com"
    private val accountA = CalendarAccount(a, "com.google")
    private val accountB = CalendarAccount(b, "com.google")
    private val start = Instant.parse("2026-06-11T10:00:00Z")
    private val fake = FakeCalendarSource(listOf(calendar(1, accountA), calendar(2, accountB)))
    private var source: CalendarSource = fake
    private val requester = RecordingSyncRequester()
    private val retry = RecordingRetry()
    private val clock = Clock.fixed(start.minusSeconds(86_400), ZoneOffset.UTC)

    private fun calendar(id: Long, account: CalendarAccount) = CalendarInfo(
        CalendarId(id),
        account,
        "c$id",
        0,
        CalendarAccess.OWNER,
        ownerEmail = account.name
    )

    private fun answers() = ForeignAnswers(OwnCopies(source, clock), source, requester, retry)

    private fun draft(calendar: Long, title: String = "Dinner") = EventDraft(
        calendarId = CalendarId(calendar),
        title = title,
        time = EventTime.Timed(start, start.plusSeconds(3600), ZoneOffset.UTC),
        attendees = listOf(
            Attendee.of(a, status = AttendeeStatus.ACCEPTED, isOrganizer = true),
            Attendee.of(b)
        )
    )

    private suspend fun create(
        calendar: Long,
        uid: String? = "uid-1",
        title: String = "Dinner"
    ): EventId = (fake.create(draft(calendar, title)) as CalendarResult.Success).value.also {
        fake.setUid(it, uid)
    }

    private suspend fun statusOf(id: EventId, email: String) =
        (fake.event(id) as CalendarResult.Success).value.attendees.first {
            it.email == email
        }.status

    private fun key(event: EventId) = InvitationKey(CalendarId(1), event, b)

    @Test
    fun `with B's own copy the answer is written there and A's event is left alone`() = runTest {
        val organizers = create(1)
        val copy = create(2)

        val outcome = answers().answer(key(organizers), AttendeeStatus.ACCEPTED)

        assertEquals(ResponseOutcome.Answered, outcome)
        assertEquals(AttendeeStatus.ACCEPTED, statusOf(copy, b))
        assertEquals(AttendeeStatus.NEEDS_ACTION, statusOf(organizers, b))
        assertEquals(emptyList<Any>(), retry.scheduled)
    }

    @Test
    fun `an unrelated event in B's calendar is not its copy`() = runTest {
        val organizers = create(1)
        create(2, uid = "another")
        create(2, uid = null)

        val outcome = answers().answer(key(organizers), AttendeeStatus.ACCEPTED)

        assertEquals(ResponseOutcome.WaitingForAccount(b), outcome)
    }

    @Test
    fun `without B's copy nothing is written, B is asked to sync and the answer is kept`() =
        runTest {
            val organizers = create(1)

            val outcome = answers().answer(key(organizers), AttendeeStatus.DECLINED)

            assertEquals(ResponseOutcome.WaitingForAccount(b), outcome)
            assertEquals(AttendeeStatus.NEEDS_ACTION, statusOf(organizers, b))
            assertEquals(listOf(setOf(accountB)), requester.requests)
            assertEquals(listOf(SyncReason.MANUAL), requester.reasons)
            assertEquals(listOf(key(organizers) to AttendeeStatus.DECLINED), retry.scheduled)
        }

    @Test
    fun `a deleted event, or an account no longer on the phone, leaves nothing to answer`() =
        runTest {
            val organizers = create(1)
            val orphan = InvitationKey(CalendarId(1), organizers, "gone@gmail.com")
            assertEquals(
                ResponseOutcome.Gone,
                answers().answer(orphan, AttendeeStatus.ACCEPTED)
            )
            fake.delete(organizers)

            assertEquals(
                ResponseOutcome.Gone,
                answers().answer(key(organizers), AttendeeStatus.ACCEPTED)
            )
            assertEquals(emptyList<Any>(), retry.scheduled)
        }

    @Test
    fun `a source that cannot tell fails the answer without waiting`() = runTest {
        val organizers = create(1)
        val error = CalendarError.SourceFailure("down")
        val failures = listOf<CalendarSource>(
            object : CalendarSource by fake {
                override suspend fun event(id: EventId) = CalendarResult.Failure(error)
            },
            object : CalendarSource by fake {
                override suspend fun calendars() = CalendarResult.Failure(error)
            },
            object : CalendarSource by fake {
                override suspend fun instances(range: TimeRange, calendarIds: Set<CalendarId>?) =
                    CalendarResult.Failure(error)
            }
        )

        failures.forEach {
            source = it
            val outcome = answers().answer(key(organizers), AttendeeStatus.ACCEPTED)
            assertEquals(ResponseOutcome.Failed(error), outcome)
        }
        assertEquals(emptyList<Any>(), retry.scheduled)
    }

    @Test
    fun `a copy that cannot be read is skipped, and one that rejects the answer fails`() = runTest {
        val organizers = create(1)
        val copy = create(2)
        val ghost = EventInstance(EventId(99), CalendarId(2), "x", draft(2).time)
        source = object : CalendarSource by fake {
            override suspend fun instances(range: TimeRange, calendarIds: Set<CalendarId>?) =
                CalendarResult.Success(
                    listOf(ghost) +
                        (fake.instances(range, calendarIds) as CalendarResult.Success).value
                )
        }
        assertEquals(
            ResponseOutcome.Answered,
            answers().answer(key(organizers), AttendeeStatus.TENTATIVE)
        )
        assertEquals(AttendeeStatus.TENTATIVE, statusOf(copy, b))

        source = object : CalendarSource by fake {
            override suspend fun respond(id: EventId, status: AttendeeStatus) =
                CalendarResult.Failure(CalendarError.ReadOnly)
        }
        assertEquals(
            ResponseOutcome.Failed(CalendarError.ReadOnly),
            answers().answer(key(organizers), AttendeeStatus.ACCEPTED)
        )
    }

    @Test
    fun `a try finds the copy and answers it`() = runTest {
        val organizers = create(1)
        val copy = create(2)

        val attempt = answers().attempt(key(organizers), AttendeeStatus.ACCEPTED, last = false)

        assertEquals(AnswerAttempt.ANSWERED, attempt)
        assertEquals(AttendeeStatus.ACCEPTED, statusOf(copy, b))
    }

    @Test
    fun `a try without the copy asks B to sync again, until the last one gives up`() = runTest {
        val organizers = create(1)

        assertEquals(
            AnswerAttempt.RETRY,
            answers().attempt(key(organizers), AttendeeStatus.ACCEPTED, last = false)
        )
        assertEquals(listOf(setOf(accountB)), requester.requests)
        assertEquals(
            AnswerAttempt.GAVE_UP,
            answers().attempt(key(organizers), AttendeeStatus.ACCEPTED, last = true)
        )
        assertEquals(1, requester.requests.size)
    }

    @Test
    fun `a try on an event that is gone is done`() = runTest {
        val organizers = create(1)
        fake.delete(organizers)

        assertEquals(
            AnswerAttempt.GONE,
            answers().attempt(key(organizers), AttendeeStatus.ACCEPTED, last = false)
        )
    }

    @Test
    fun `a try whose source fails or whose copy refuses is retried, then given up`() = runTest {
        val organizers = create(1)
        create(2)
        source = object : CalendarSource by fake {
            override suspend fun calendars() =
                CalendarResult.Failure(CalendarError.SourceFailure("down"))
        }
        assertEquals(
            AnswerAttempt.RETRY,
            answers().attempt(key(organizers), AttendeeStatus.ACCEPTED, last = false)
        )
        assertEquals(
            AnswerAttempt.GAVE_UP,
            answers().attempt(key(organizers), AttendeeStatus.ACCEPTED, last = true)
        )

        source = object : CalendarSource by fake {
            override suspend fun respond(id: EventId, status: AttendeeStatus) =
                CalendarResult.Failure(CalendarError.ReadOnly)
        }
        assertEquals(
            AnswerAttempt.RETRY,
            answers().attempt(key(organizers), AttendeeStatus.ACCEPTED, last = false)
        )
        assertEquals(
            AnswerAttempt.GAVE_UP,
            answers().attempt(key(organizers), AttendeeStatus.ACCEPTED, last = true)
        )
    }

    @Test
    fun `a copy deleted between finding and answering is done`() = runTest {
        val organizers = create(1)
        source = object : CalendarSource by fake {
            override suspend fun respond(id: EventId, status: AttendeeStatus) =
                CalendarResult.Failure(CalendarError.NotFound)
        }
        create(2)

        assertEquals(
            AnswerAttempt.GONE,
            answers().attempt(key(organizers), AttendeeStatus.ACCEPTED, last = false)
        )
    }

    @Test
    fun `the lookup says where the copy is, or which accounts to ask for it`() = runTest {
        val organizers = create(1)
        val copy = create(2)
        val lonely = create(1, uid = "lonely")

        assertEquals(CopyLookup.Found(copy), OwnCopies(fake, clock).find(key(organizers)))
        assertEquals(
            CopyLookup.Missing(setOf(accountB)),
            OwnCopies(fake, clock).find(key(lonely))
        )
    }
}
