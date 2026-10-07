// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import com.qtekfun.ultimatecalendar.data.sync.InvitationSyncs
import com.qtekfun.ultimatecalendar.data.sync.SourceSyncRequester
import com.qtekfun.ultimatecalendar.data.sync.SyncReason
import com.qtekfun.ultimatecalendar.data.sync.SyncRequests
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InvitationSyncingSourceTest {
    private val google = CalendarAccount("me@example.com", "com.google")
    private val device = CalendarAccount("Device", "LOCAL")
    private val feeds = CalendarAccount("feeds", CalendarAccount.SUBSCRIPTION_TYPE)
    private val start = Instant.parse("2026-06-10T10:00:00Z")
    private val zoe = Attendee.of("zoe@example.com", "Zoe")
    private val me = Attendee.of("me@example.com")
    private val asked = mutableListOf<Pair<Set<CalendarAccount>, SyncReason>>()
    private var answer = SyncRequests(requested = 1, failed = 0)

    private fun calendar(id: Long, account: CalendarAccount) = CalendarInfo(
        CalendarId(id),
        account,
        "Calendar $id",
        0,
        CalendarAccess.OWNER,
        ownerEmail = "me@example.com"
    )

    private val fake = FakeCalendarSource(
        listOf(calendar(1, google), calendar(2, device), calendar(3, feeds))
    )

    private val requester = object : SourceSyncRequester {
        override suspend fun requestSync(accounts: Set<CalendarAccount>, reason: SyncReason) =
            answer.also { asked += accounts to reason }
    }

    private fun TestScope.source() =
        InvitationSyncingSource(fake, InvitationSyncs(requester, backgroundScope, WAIT_MS))

    private fun draft(
        calendar: Long = 1,
        guests: List<Attendee> = listOf(zoe),
        title: String = "Lunch",
        rrule: String? = null
    ) = EventDraft(
        CalendarId(calendar),
        title,
        EventTime.Timed(start, start.plusSeconds(3600), ZoneOffset.UTC),
        attendees = guests,
        rrule = rrule
    )

    private suspend fun InvitationSyncingSource.created(draft: EventDraft): EventId =
        (create(draft) as CalendarResult.Success).value

    private suspend fun InvitationSyncingSource.stored(id: EventId): Event =
        (event(id) as CalendarResult.Success).value

    private suspend fun TestScope.settle() {
        advanceTimeBy(WAIT_MS + 1)
        runCurrent()
    }

    private fun assertAsked(vararg accounts: CalendarAccount) =
        assertEquals(accounts.map { setOf(it) to SyncReason.WRITE }, asked)

    @Test
    fun `an event created with guests asks its account to sync`() = runTest {
        source().created(draft())
        settle()

        assertAsked(google)
    }

    @Test
    fun `an event created without guests asks nobody`() = runTest {
        source().created(draft(guests = emptyList()))
        settle()

        assertAsked()
    }

    @Test
    fun `guests in the on-device or a subscription calendar ask nobody`() = runTest {
        val source = source()
        source.created(draft(calendar = 2))
        source.created(draft(calendar = 3))
        settle()

        assertAsked()
    }

    @Test
    fun `a burst of writes is one request`() = runTest {
        val source = source()
        val id = source.created(draft())
        source.created(draft(title = "Dinner"))
        source.update(source.stored(id).copy(title = "Brunch"))
        source.delete(id)
        settle()

        assertAsked(google)
    }

    @Test
    fun `a change the guests see asks, a color or a reminder does not`() = runTest {
        val source = source()
        val id = source.created(draft())
        settle()
        asked.clear()

        source.update(source.stored(id).copy(color = 0xFF0000))
        settle()
        assertAsked()

        source.update(source.stored(id).copy(location = "Room 2"))
        settle()
        assertAsked(google)
    }

    @Test
    fun `changing the time, the notes or the guests asks`() = runTest {
        val source = source()
        val id = source.created(draft())
        val before = source.stored(id)
        settle()
        asked.clear()

        val later = before.time.let { it as EventTime.Timed }
        source.update(before.copy(time = later.copy(start = start.plusSeconds(1800))))
        settle()
        source.update(source.stored(id).copy(description = "Bring the plan"))
        settle()
        source.update(
            source.stored(id).copy(attendees = listOf(zoe, Attendee.of("ana@example.com")))
        )
        settle()

        assertAsked(google, google, google)
    }

    @Test
    fun `an event that gets or loses its guests asks, one that never had any does not`() = runTest {
        val source = source()
        val alone = source.created(draft(guests = emptyList()))
        settle()
        source.update(source.stored(alone).copy(title = "Alone, later"))
        settle()
        assertAsked()

        source.update(source.stored(alone).copy(attendees = listOf(zoe)))
        settle()
        assertAsked(google)

        asked.clear()
        source.update(source.stored(alone).copy(attendees = emptyList()))
        settle()
        assertAsked(google)
    }

    @Test
    fun `deleting an event with guests asks, without guests it does not`() = runTest {
        val source = source()
        val withGuests = source.created(draft())
        val alone = source.created(draft(guests = emptyList()))
        settle()
        asked.clear()

        source.delete(alone)
        settle()
        assertAsked()

        source.delete(withGuests)
        settle()
        assertAsked(google)
    }

    @Test
    fun `cancelling or changing one occurrence of a series with guests asks`() = runTest {
        val source = source()
        val series = source.created(draft(rrule = "FREQ=DAILY;COUNT=4"))
        settle()
        asked.clear()

        source.cancelInstance(series, start)
        settle()
        source.editInstance(series, start.plusSeconds(DAY), draft(title = "Once"))
        settle()

        assertAsked(google, google)
    }

    @Test
    fun `an occurrence of a series without guests asks nobody`() = runTest {
        val source = source()
        val series = source.created(draft(guests = emptyList(), rrule = "FREQ=DAILY;COUNT=4"))
        settle()

        source.cancelInstance(series, start)
        source.editInstance(series, start.plusSeconds(DAY), draft(guests = emptyList()))
        settle()

        assertAsked()
    }

    @Test
    fun `answering an invitation asks the account of its calendar`() = runTest {
        val source = source()
        val id = source.created(draft(guests = listOf(me, zoe)))
        settle()
        asked.clear()

        source.respond(id, AttendeeStatus.ACCEPTED)
        settle()

        assertAsked(google)
    }

    @Test
    fun `a write that fails asks nobody and its result is passed on`() = runTest {
        val source = source()

        val deleted = source.delete(EventId(404))
        val answered = source.respond(EventId(404), AttendeeStatus.ACCEPTED)
        settle()

        assertEquals(fake.delete(EventId(404)), deleted)
        assertEquals(fake.respond(EventId(404), AttendeeStatus.ACCEPTED), answered)
        assertAsked()
    }

    @Test
    fun `reads pass through`() = runTest {
        val source = source()
        val id = source.created(draft(guests = emptyList()))

        assertEquals(fake.event(id), source.event(id))
        assertEquals(fake.calendars(), source.calendars())
    }

    private companion object {
        const val WAIT_MS = 1_500L
        const val DAY = 86_400L
    }
}
