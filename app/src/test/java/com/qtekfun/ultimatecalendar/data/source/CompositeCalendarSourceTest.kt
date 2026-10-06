// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
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
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Routing and merging of the composite, with scripted sources (the contract covers the rest). */
class CompositeCalendarSourceTest {
    private val phone = CalendarId(1)
    private val cloud = CalDavIds.calendar(1)
    private val phoneEvent = EventId(1)
    private val cloudEvent = CalDavIds.event(1)
    private val start = Instant.parse("2026-10-06T10:00:00Z")
    private val range = TimeRange(start, start.plusSeconds(3600))
    private val provider = mockk<CalendarSource>()
    private val caldav = mockk<CalendarSource>()
    private val feeds = mockk<CalendarSource>()
    private val source = CompositeCalendarSource(provider, caldav, feeds)

    // No subscriptions, unless a test says otherwise.
    init {
        coEvery { feeds.calendars() } returns CalendarResult.Success(emptyList())
        coEvery { feeds.instances(any(), any()) } returns CalendarResult.Success(emptyList())
        coEvery { feeds.search(any(), any(), any()) } returns CalendarResult.Success(emptyList())
        every { feeds.changes } returns emptyFlow()
    }

    private fun instance(id: EventId, calendar: CalendarId, at: Instant) = EventInstance(
        id,
        calendar,
        "t",
        EventTime.Timed(at, at.plusSeconds(60), ZoneOffset.UTC)
    )

    private fun calendar(id: CalendarId) =
        CalendarInfo(id, CalendarAccount("a", "LOCAL"), "c", 0, CalendarAccess.OWNER)

    private fun ok(vararg items: EventInstance) = CalendarResult.Success(items.toList())

    @Test
    fun `calendars of both sources come together, the provider's first`() = runTest {
        coEvery { provider.calendars() } returns CalendarResult.Success(listOf(calendar(phone)))
        coEvery { caldav.calendars() } returns CalendarResult.Success(listOf(calendar(cloud)))

        assertEquals(listOf(phone, cloud), source.calendars().getOrNull()!!.map { it.id })
    }

    @Test
    fun `without CalDAV calendars the provider's own answer comes back as it is`() = runTest {
        val answer = CalendarResult.Success(listOf(calendar(phone)))
        coEvery { provider.calendars() } returns answer
        coEvery { caldav.calendars() } returns CalendarResult.Success(emptyList())
        assertTrue(source.calendars() === answer)

        val denied = CalendarResult.Failure(CalendarError.PermissionDenied)
        coEvery { provider.calendars() } returns denied
        assertTrue(source.calendars() === denied)
        val broken = CalendarResult.Failure(CalendarError.SourceFailure("gone"))
        coEvery { provider.calendars() } returns broken
        assertTrue(source.calendars() === broken)
    }

    @Test
    fun `a missing permission leaves the CalDAV calendars and is told apart`() = runTest {
        val denied = CalendarResult.Failure(CalendarError.PermissionDenied)
        coEvery { provider.calendars() } returns denied
        coEvery { caldav.calendars() } returns CalendarResult.Success(listOf(calendar(cloud)))

        assertEquals(listOf(cloud), source.calendars().getOrNull()!!.map { it.id })
        assertTrue(source.denied.value)
    }

    @Test
    fun `a missing permission leaves the subscriptions when there is no CalDAV account`() =
        runTest {
            val feed = SubscriptionIds.calendar(9)
            coEvery { provider.calendars() } returns
                CalendarResult.Failure(CalendarError.PermissionDenied)
            coEvery { caldav.calendars() } returns CalendarResult.Success(emptyList())
            coEvery { feeds.calendars() } returns CalendarResult.Success(listOf(calendar(feed)))

            assertEquals(listOf(feed), source.calendars().getOrNull()!!.map { it.id })
        }

    @Test
    fun `a missing permission is the answer when no source has a calendar`() = runTest {
        val denied = CalendarResult.Failure(CalendarError.PermissionDenied)
        coEvery { provider.calendars() } returns denied
        coEvery { caldav.calendars() } returns CalendarResult.Success(emptyList())

        assertTrue(source.calendars() === denied)
        assertTrue(source.denied.value)
    }

    @Test
    fun `a missing permission is the answer when the others failed too`() = runTest {
        val denied = CalendarResult.Failure(CalendarError.PermissionDenied)
        coEvery { provider.calendars() } returns denied
        coEvery { caldav.calendars() } returns
            CalendarResult.Failure(CalendarError.SourceFailure("db"))

        assertTrue(source.calendars() === denied)
    }

    @Test
    fun `access is granted until the provider says otherwise and again once it answers`() =
        runTest {
            coEvery { caldav.calendars() } returns CalendarResult.Success(emptyList())
            assertEquals(false, source.denied.value)

            coEvery { provider.calendars() } returns
                CalendarResult.Failure(CalendarError.PermissionDenied)
            source.calendars()
            assertTrue(source.denied.value)

            coEvery { provider.calendars() } returns CalendarResult.Success(listOf(calendar(phone)))
            source.calendars()
            assertEquals(false, source.denied.value)

            // Another failure of the provider is not a missing permission.
            coEvery { provider.calendars() } returns
                CalendarResult.Failure(CalendarError.SourceFailure("gone"))
            source.calendars()
            assertEquals(false, source.denied.value)
        }

    @Test
    fun `without the permission, CalDAV events still come when every calendar is asked`() =
        runTest {
            val denied = CalendarResult.Failure(CalendarError.PermissionDenied)
            val event = instance(cloudEvent, cloud, start)
            coEvery { provider.instances(range, null) } returns denied
            coEvery { caldav.instances(range, null) } returns ok(event)

            assertEquals(listOf(event), source.instances(range).getOrNull())
        }

    @Test
    fun `a CalDAV calendar without events in the range is not a missing permission`() = runTest {
        coEvery { provider.instances(range, null) } returns
            CalendarResult.Failure(CalendarError.PermissionDenied)
        coEvery { caldav.instances(range, null) } returns ok()
        coEvery { caldav.calendars() } returns CalendarResult.Success(listOf(calendar(cloud)))

        assertEquals(
            CalendarResult.Success(emptyList<EventInstance>()),
            source.instances(range)
        )
    }

    @Test
    fun `an empty answer is a missing permission when there is no CalDAV calendar or feed`() =
        runTest {
            val denied = CalendarResult.Failure(CalendarError.PermissionDenied)
            coEvery { provider.instances(range, null) } returns denied
            coEvery { caldav.instances(range, null) } returns ok()
            coEvery { caldav.calendars() } returns CalendarResult.Success(emptyList())

            assertTrue(source.instances(range) === denied)
        }

    @Test
    fun `a failure of one source leaves the calendars of the other`() = runTest {
        coEvery { provider.calendars() } returns
            CalendarResult.Failure(CalendarError.SourceFailure("x"))
        coEvery { caldav.calendars() } returns CalendarResult.Success(listOf(calendar(cloud)))
        assertEquals(listOf(cloud), source.calendars().getOrNull()!!.map { it.id })

        val fine = CalendarResult.Success(listOf(calendar(phone)))
        coEvery { provider.calendars() } returns fine
        coEvery { caldav.calendars() } returns
            CalendarResult.Failure(CalendarError.SourceFailure("db"))
        assertEquals(fine, source.calendars())
    }

    @Test
    fun `instances are merged by start and each source only gets its own calendars`() = runTest {
        val early = instance(phoneEvent, phone, start)
        val late = instance(cloudEvent, cloud, start.plusSeconds(60))
        val first = instance(cloudEvent, cloud, start.minusSeconds(60))
        coEvery { provider.instances(range, null) } returns ok(early)
        coEvery { caldav.instances(range, null) } returns ok(late, first)

        assertEquals(listOf(first, early, late), source.instances(range).getOrNull())

        coEvery { provider.instances(range, setOf(phone)) } returns ok(early)
        coEvery { caldav.instances(range, setOf(cloud)) } returns ok(late)
        assertEquals(listOf(early, late), source.instances(range, setOf(phone, cloud)).getOrNull())
    }

    @Test
    fun `a source none of the asked calendars belong to is not asked`() = runTest {
        coEvery { caldav.instances(range, setOf(cloud)) } returns
            ok(instance(cloudEvent, cloud, start))

        assertEquals(1, source.instances(range, setOf(cloud)).getOrNull()!!.size)
        assertEquals(0, source.instances(range, emptySet()).getOrNull()!!.size)
        coVerify(exactly = 0) { provider.instances(any(), any()) }
    }

    @Test
    fun `searches merge both and are limited to the calendars asked`() = runTest {
        val found = SearchableEvent(phoneEvent, phone, "a", instance(phoneEvent, phone, start).time)
        val other = SearchableEvent(cloudEvent, cloud, "b", found.time)
        coEvery { provider.search("a", null, range) } returns CalendarResult.Success(listOf(found))
        coEvery { caldav.search("a", null, range) } returns CalendarResult.Success(listOf(other))
        coEvery { caldav.search("a", setOf(cloud), null) } returns
            CalendarResult.Success(listOf(other))

        assertEquals(listOf(found, other), source.search("a", null, range).getOrNull())
        assertEquals(listOf(other), source.search("a", setOf(cloud)).getOrNull())
    }

    @Test
    fun `events are read and changed in the source that owns them`() = runTest {
        val event = mockk<Event> {
            every { id } returns cloudEvent
            every { calendarId } returns
                cloud
        }
        val draft = EventDraft(cloud, "t", instance(cloudEvent, cloud, start).time)
        coEvery { caldav.event(cloudEvent) } returns CalendarResult.Success(event)
        coEvery { caldav.create(draft) } returns CalendarResult.Success(cloudEvent)
        coEvery { caldav.update(event) } returns CalendarResult.Success(Unit)
        coEvery { caldav.delete(cloudEvent) } returns CalendarResult.Success(Unit)
        coEvery { caldav.editInstance(cloudEvent, start, draft) } returns
            CalendarResult.Success(Unit)
        coEvery { caldav.cancelInstance(cloudEvent, start) } returns CalendarResult.Success(Unit)
        coEvery { caldav.respond(cloudEvent, AttendeeStatus.ACCEPTED) } returns
            CalendarResult.Success(Unit)
        coEvery { provider.event(phoneEvent) } returns
            CalendarResult.Failure(CalendarError.NotFound)
        coEvery { provider.delete(phoneEvent) } returns CalendarResult.Success(Unit)
        coEvery { provider.create(draft.copy(calendarId = phone)) } returns
            CalendarResult.Success(phoneEvent)

        assertEquals(CalendarResult.Success(event), source.event(cloudEvent))
        assertEquals(CalendarResult.Success(cloudEvent), source.create(draft))
        assertEquals(CalendarResult.Success(Unit), source.update(event))
        assertEquals(CalendarResult.Success(Unit), source.delete(cloudEvent))
        assertEquals(CalendarResult.Success(Unit), source.editInstance(cloudEvent, start, draft))
        assertEquals(CalendarResult.Success(Unit), source.cancelInstance(cloudEvent, start))
        assertEquals(
            CalendarResult.Success(Unit),
            source.respond(cloudEvent, AttendeeStatus.ACCEPTED)
        )
        assertEquals(CalendarResult.Failure(CalendarError.NotFound), source.event(phoneEvent))
        assertEquals(CalendarResult.Success(Unit), source.delete(phoneEvent))
        assertEquals(
            CalendarResult.Success(phoneEvent),
            source.create(draft.copy(calendarId = phone))
        )
    }

    @Test
    fun `an event cannot be moved from one source to the other`() = runTest {
        val event = mockk<Event> {
            every { id } returns phoneEvent
            every { calendarId } returns
                cloud
        }

        assertEquals(
            CalendarResult.Failure(CalendarError.Invalid("an event cannot change source")),
            source.update(event)
        )
    }

    @Test
    fun `the changes of both sources arrive`() = runTest {
        val fromProvider = MutableSharedFlow<Unit>()
        val fromCalDav = MutableSharedFlow<Unit>()
        every { provider.changes } returns fromProvider
        every { caldav.changes } returns fromCalDav

        source.changes.test {
            fromProvider.subscriptionCount.first { it == 1 }
            fromCalDav.subscriptionCount.first { it == 1 }
            fromProvider.emit(Unit)
            assertEquals(Unit, awaitItem())
            fromCalDav.emit(Unit)
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val feed = SubscriptionIds.calendar(1)
    private val feedEvent = SubscriptionIds.event(1)

    @Test
    fun `subscriptions join the calendars, the instances and the searches`() = runTest {
        coEvery { provider.calendars() } returns CalendarResult.Success(listOf(calendar(phone)))
        coEvery { caldav.calendars() } returns CalendarResult.Success(listOf(calendar(cloud)))
        coEvery { feeds.calendars() } returns CalendarResult.Success(listOf(calendar(feed)))
        assertEquals(listOf(phone, cloud, feed), source.calendars().getOrNull()!!.map { it.id })

        val mine = instance(phoneEvent, phone, start)
        val theirs = instance(feedEvent, feed, start.minusSeconds(60))
        coEvery { provider.instances(range, null) } returns ok(mine)
        coEvery { caldav.instances(range, null) } returns ok()
        coEvery { feeds.instances(range, null) } returns ok(theirs)
        assertEquals(listOf(theirs, mine), source.instances(range).getOrNull())

        val found = SearchableEvent(feedEvent, feed, "a", mine.time)
        coEvery { provider.search("a", null, null) } returns CalendarResult.Success(emptyList())
        coEvery { caldav.search("a", null, null) } returns CalendarResult.Success(emptyList())
        coEvery { feeds.search("a", null, null) } returns CalendarResult.Success(listOf(found))
        assertEquals(listOf(found), source.search("a").getOrNull())
    }

    @Test
    fun `each source only gets the calendars of its own kind`() = runTest {
        coEvery { feeds.instances(range, setOf(feed)) } returns
            ok(instance(feedEvent, feed, start))
        coEvery { provider.instances(range, setOf(phone)) } returns
            ok(instance(phoneEvent, phone, start))

        assertEquals(2, source.instances(range, setOf(phone, feed)).getOrNull()!!.size)
        coVerify(exactly = 0) { caldav.instances(any(), any()) }
    }

    @Test
    fun `a subscription's events are read and refused in the source that owns them`() = runTest {
        val event = mockk<Event> {
            every { id } returns feedEvent
            every { calendarId } returns feed
        }
        val draft = EventDraft(feed, "t", instance(feedEvent, feed, start).time)
        val readOnly = CalendarResult.Failure(CalendarError.ReadOnly)
        coEvery { feeds.event(feedEvent) } returns CalendarResult.Success(event)
        coEvery { feeds.create(draft) } returns readOnly
        coEvery { feeds.update(event) } returns readOnly
        coEvery { feeds.delete(feedEvent) } returns readOnly
        coEvery { feeds.respond(feedEvent, AttendeeStatus.ACCEPTED) } returns readOnly

        assertEquals(CalendarResult.Success(event), source.event(feedEvent))
        assertEquals(readOnly, source.create(draft))
        assertEquals(readOnly, source.update(event))
        assertEquals(readOnly, source.delete(feedEvent))
        assertEquals(readOnly, source.respond(feedEvent, AttendeeStatus.ACCEPTED))
        coVerify(exactly = 0) { provider.create(any()) }
        coVerify(exactly = 0) { caldav.create(any()) }
    }

    @Test
    fun `a failing subscription source never hides the other calendars`() = runTest {
        val fine = CalendarResult.Success(listOf(calendar(phone)))
        coEvery { provider.calendars() } returns fine
        coEvery { caldav.calendars() } returns CalendarResult.Success(emptyList())
        coEvery { feeds.calendars() } returns
            CalendarResult.Failure(CalendarError.SourceFailure("db"))

        assertEquals(fine, source.calendars())
    }

    @Test
    fun `the changes of the subscriptions arrive too`() = runTest {
        val fromFeeds = MutableSharedFlow<Unit>()
        every { provider.changes } returns emptyFlow()
        every { caldav.changes } returns emptyFlow()
        every { feeds.changes } returns fromFeeds

        source.changes.test {
            fromFeeds.subscriptionCount.first { it == 1 }
            fromFeeds.emit(Unit)
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
