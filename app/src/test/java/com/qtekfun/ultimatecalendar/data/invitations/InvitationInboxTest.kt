// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.invitations

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.calendar
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.invitation
import com.qtekfun.ultimatecalendar.sync.CheckFixtures.now
import com.qtekfun.ultimatecalendar.sync.FixedCheckSettings
import com.qtekfun.ultimatecalendar.sync.InMemoryNotifiedDao
import com.qtekfun.ultimatecalendar.sync.InvitationChecker
import com.qtekfun.ultimatecalendar.sync.MutableClock
import com.qtekfun.ultimatecalendar.sync.RecordingNotifier
import com.qtekfun.ultimatecalendar.sync.RecordingSyncRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvitationInboxTest {
    private val source = FakeCalendarSource(listOf(calendar(1), calendar(2)))
    private val notifier = RecordingNotifier()
    private val syncs = RecordingSyncRequester()
    private val notified = NotifiedInvitations(InMemoryNotifiedDao(), Dispatchers.Unconfined)

    private fun checker(on: CalendarSource = source) = InvitationChecker(
        on,
        syncs,
        notified,
        notifier,
        FixedCheckSettings(),
        MutableClock(now),
        Dispatchers.Unconfined
    )

    private suspend fun create(title: String, calendar: Long = 1, hours: Long = 1) = (
        source.create(
            invitation(title, calendar = calendar, start = now.plusSeconds(hours * 3600))
        )
            as CalendarResult.Success
        ).value

    private suspend fun titles(on: InvitationChecker = checker()) =
        (on.pending() as CalendarResult.Success).value.map { it.title }

    @Test
    fun `pending lists every calendar soonest first without notifying or recording`() = runTest {
        create("Dinner", calendar = 2, hours = 5)
        create("Lunch", calendar = 1, hours = 1)

        assertEquals(listOf("Lunch", "Dinner"), titles())
        assertTrue(notifier.calls.isEmpty())
        assertTrue(notified.load().isEmpty())
        assertTrue(syncs.requests.isEmpty())
    }

    @Test
    fun `pending leaves out what was answered and what is not for the user`() = runTest {
        val answered = create("Answered")
        source.respond(answered, AttendeeStatus.ACCEPTED)
        source.create(invitation("Not mine", attendee = "other@example.com"))
        create("Open", hours = 2)

        assertEquals(listOf("Open"), titles())
    }

    @Test
    fun `a calendar that cannot be read is reported, not hidden as empty`() = runTest {
        val broken = object : CalendarSource by source {
            override suspend fun calendars(): CalendarResult<List<CalendarInfo>> =
                CalendarResult.Failure(CalendarError.SourceFailure("down"))
        }
        val result = checker(broken).pending()
        assertInstanceOf(CalendarResult.Failure::class.java, result)
    }

    @Test
    fun `a revoked permission is reported as such`() = runTest {
        val revoked = object : CalendarSource by source {
            override suspend fun event(
                id: EventId
            ): CalendarResult<com.qtekfun.ultimatecalendar.domain.model.Event> =
                throw SecurityException("revoked")
        }
        create("Lunch")
        assertEquals(
            CalendarResult.Failure(CalendarError.PermissionDenied),
            checker(revoked).pending()
        )
    }

    @Test
    fun `the inbox emits the list at once and again when asked`() = runTest {
        create("Lunch")
        val inbox = InvitationInbox(checker())

        inbox.pending().test {
            assertEquals(listOf("Lunch"), awaitItem().map { it.title })
            create("Dinner", hours = 3)
            inbox.refresh()
            assertEquals(listOf("Lunch", "Dinner"), awaitItem().map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed read keeps the last list and emits nothing`() = runTest {
        var broken = false
        val flaky = object : CalendarSource by source {
            override suspend fun calendars(): CalendarResult<List<CalendarInfo>> = if (broken) {
                CalendarResult.Failure(CalendarError.SourceFailure("down"))
            } else {
                source.calendars()
            }
        }
        create("Lunch")
        val inbox = InvitationInbox(checker(flaky))

        inbox.pending().test {
            assertEquals(listOf("Lunch"), awaitItem().map { it.title })
            broken = true
            inbox.refresh()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
