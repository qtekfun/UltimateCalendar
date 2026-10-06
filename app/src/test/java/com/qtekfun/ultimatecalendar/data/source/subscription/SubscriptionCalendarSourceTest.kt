// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.subscription

import com.qtekfun.ultimatecalendar.data.source.CompositeCalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.data.source.SubscriptionIds
import com.qtekfun.ultimatecalendar.data.subscriptions.FeedIcs
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionRig
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The read-only source over a real in-memory Room, fed by the real downloader and refresher. The
 * shared `CalendarSourceContract` needs a calendar the user can write to, which a subscription
 * never is, so what a read-only source must do is checked here.
 */
class SubscriptionCalendarSourceTest {
    @StartStop
    val server = MockWebServer()

    private val rig by lazy { SubscriptionRig(server) }
    private val source get() = rig.source
    private val month = TimeRange(
        Instant.parse("2026-10-01T00:00:00Z"),
        Instant.parse("2026-11-01T00:00:00Z")
    )
    private val madrid = ZoneId.of("Europe/Madrid")
    private var paths = 0

    @AfterEach
    fun close() = rig.close()

    /** A subscription with [events], downloaded. Returns its row. */
    private suspend fun load(
        vararg events: String,
        name: String = "Holidays",
        enabled: Boolean = true
    ): Long {
        val id = rig.addRow("/feed${paths++}.ics", name)
        server.enqueue(MockResponse(200, okhttp3.Headers.headersOf(), FeedIcs.calendar(*events)))
        rig.refresher.refresh(id)
        if (!enabled) rig.subscriptions.update(rig.subscriptions.get(id)!!.copy(enabled = false))
        return id
    }

    private suspend fun instances(
        range: TimeRange = month,
        ids: Set<CalendarId>? = null
    ): List<EventInstance> = (source.instances(range, ids) as CalendarResult.Success).value

    private fun <T> CalendarResult<T>.value(): T = (this as CalendarResult.Success).value

    @Test
    fun `the calendars are the enabled subscriptions, read only, with their colors`() = runTest {
        val on = load(FeedIcs.event("a@x", "A"), name = "On")
        load(FeedIcs.event("b@x", "B"), name = "Off", enabled = false)

        val calendars = source.calendars().value()

        val calendar = calendars.single()
        assertEquals(SubscriptionIds.calendar(on), calendar.id)
        assertEquals("On", calendar.displayName)
        assertEquals(0xFF039BE5.toInt(), calendar.color)
        assertEquals(CalendarAccess.READ, calendar.access)
        assertFalse(calendar.access.canRespond)
        assertFalse(calendar.access.canCreate)
        assertTrue(calendar.account.isSubscription)
        assertNull(calendar.ownerEmail)
        assertTrue(calendar.visible)
    }

    @Test
    fun `instances are limited to the range, sorted and in the subscription's id space`() =
        runTest {
            val id = load(
                FeedIcs.event("late@x", "Late", "20261006T150000Z", "20261006T160000Z"),
                FeedIcs.event("early@x", "Early"),
                FeedIcs.event("other@x", "Other day", "20261020T100000Z", "20261020T110000Z")
            )
            val day = TimeRange(
                Instant.parse("2026-10-06T00:00:00Z"),
                Instant.parse("2026-10-07T00:00:00Z")
            )

            val found = instances(day)

            assertEquals(listOf("Early", "Late"), found.map { it.title })
            assertTrue(found.all { SubscriptionIds.isSubscription(it.eventId) })
            assertTrue(found.all { it.calendarId == SubscriptionIds.calendar(id) })
            assertEquals(3, instances().size)
        }

    @Test
    fun `an event that starts before the range but overlaps it is included`() = runTest {
        load(FeedIcs.event("n@x", "Night", "20261005T233000Z", "20261006T003000Z"))

        assertEquals(
            1,
            instances(
                TimeRange(
                    Instant.parse("2026-10-06T00:00:00Z"),
                    Instant.parse("2026-10-07T00:00:00Z")
                )
            ).size
        )
    }

    @Test
    fun `instances can be limited to some calendars and other sources' ids find nothing`() =
        runTest {
            val one = load(FeedIcs.event("a@x", "One"), name = "One")
            val two = load(FeedIcs.event("b@x", "Two"), name = "Two")

            assertEquals(
                listOf("One"),
                instances(ids = setOf(SubscriptionIds.calendar(one))).map { it.title }
            )
            assertEquals(
                setOf("One", "Two"),
                instances(ids = setOf(SubscriptionIds.calendar(one), SubscriptionIds.calendar(two)))
                    .map { it.title }.toSet()
            )
            assertEquals(emptyList<EventInstance>(), instances(ids = emptySet()))
            assertEquals(emptyList<EventInstance>(), instances(ids = setOf(CalendarId(1))))
            assertEquals(2, instances(ids = null).size)
        }

    @Test
    fun `a switched off subscription shows nowhere but keeps its events`() = runTest {
        val id = load(FeedIcs.event("a@x", "Hidden"), enabled = false)
        val eventId = SubscriptionIds.event(rig.events.inSubscription(id).single().id)

        assertEquals(emptyList<EventInstance>(), instances())
        assertEquals(emptyList<Any>(), source.search("hidden").value())
        assertEquals(CalendarResult.Failure(CalendarError.NotFound), source.event(eventId))

        rig.subscriptions.update(rig.subscriptions.get(id)!!.copy(enabled = true))

        assertEquals(listOf("Hidden"), instances().map { it.title })
        assertEquals("Hidden", source.event(eventId).value().title)
    }

    @Test
    fun `a weekly series is expanded by the engine with its exceptions and changes`() = runTest {
        val master = FeedIcs.event(
            "w@x",
            "Weekly",
            extra = "RRULE:FREQ=WEEKLY;COUNT=5\nEXDATE:20261013T100000Z\n"
        )
        val moved = "BEGIN:VEVENT\nUID:w@x\nDTSTAMP:20261001T000000Z\n" +
            "RECURRENCE-ID:20261020T100000Z\nDTSTART:20261020T120000Z\n" +
            "DTEND:20261020T130000Z\nSUMMARY:Weekly (late)\nEND:VEVENT\n"
        val cancelled = "BEGIN:VEVENT\nUID:w@x\nDTSTAMP:20261001T000000Z\n" +
            "RECURRENCE-ID:20261027T100000Z\nDTSTART:20261027T100000Z\nSTATUS:CANCELLED\n" +
            "SUMMARY:Weekly\nEND:VEVENT\n"
        load(master, moved, cancelled)

        val found = instances(TimeRange(month.start, Instant.parse("2026-11-30T00:00:00Z")))

        assertEquals(
            listOf("2026-10-06T10:00:00Z", "2026-10-20T12:00:00Z", "2026-11-03T10:00:00Z"),
            found.map { (it.time as EventTime.Timed).start.toString() }
        )
        assertEquals(listOf("Weekly", "Weekly (late)", "Weekly"), found.map { it.title })
        assertTrue(found.all { it.isRecurring })
        assertEquals(1, found.map { it.eventId }.toSet().size)
    }

    @Test
    fun `a rule the engine cannot read still shows the first occurrence`() = runTest {
        load(FeedIcs.event("odd@x", "Odd", extra = "RRULE:FREQ=SECONDLY;INTERVAL=0\n"))

        assertEquals(listOf("Odd"), instances().map { it.title })
    }

    @Test
    fun `all-day events keep their dates`() = runTest {
        load(
            FeedIcs.event("h@x", "Holiday", "20261012", "20261014")
                .replace("DTSTART:", "DTSTART;VALUE=DATE:").replace("DTEND:", "DTEND;VALUE=DATE:")
        )

        assertEquals(
            EventTime.AllDay(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14)),
            instances().single().time
        )
    }

    @Test
    fun `events with guests are not invitations and carry no reminders`() = runTest {
        load(FeedIcs.invitation(me = "me@example.com"))

        val instance = instances().single()
        val event = source.event(instance.eventId).value()

        assertNull(instance.selfStatus)
        assertFalse(instance.hasAttendees)
        assertEquals(emptyList<Any>(), event.attendees)
        assertEquals(emptyList<Any>(), event.reminders)
        assertEquals("Planning", event.title)
        assertEquals("boss@example.com", event.organizer)
    }

    @Test
    fun `search finds an event by its words without case or accents`() = runTest {
        load(
            FeedIcs.event(
                "a@x",
                "Café con Ana",
                extra = "LOCATION:Plaza Mayor\nDESCRIPTION:bring notes\n"
            ),
            FeedIcs.event("b@x", "Dentist", "20261008T100000Z", "20261008T110000Z")
        )

        assertEquals(listOf("Café con Ana"), source.search("CAFE").value().map { it.title })
        assertEquals(listOf("Café con Ana"), source.search("plaza notes").value().map { it.title })
        assertEquals(emptyList<Any>(), source.search("plaza dentist").value())
        assertEquals(emptyList<Any>(), source.search("   ").value())
        assertEquals(emptyList<Any>(), source.search("zzz").value())
    }

    @Test
    fun `search takes percent, underscore and backslash literally`() = runTest {
        load(
            FeedIcs.event("a@x", "100% done"),
            FeedIcs.event("b@x", "1000 done", "20261008T100000Z", "20261008T110000Z")
        )

        assertEquals(listOf("100% done"), source.search("100%").value().map { it.title })
    }

    @Test
    fun `search is limited to some calendars and to a range, and lists a series once`() = runTest {
        val one = load(
            FeedIcs.event("a@x", "Standup", extra = "RRULE:FREQ=WEEKLY;COUNT=4\n"),
            name = "One"
        )
        load(FeedIcs.event("b@x", "Standup too"), name = "Two")
        val week = TimeRange(
            Instant.parse("2026-10-13T00:00:00Z"),
            Instant.parse("2026-10-14T00:00:00Z")
        )

        assertEquals(2, source.search("standup").value().size)
        assertEquals(
            listOf("Standup"),
            source.search("standup", setOf(SubscriptionIds.calendar(one))).value()
                .map { it.title }
        )
        assertEquals(
            listOf("Standup"),
            source.search("standup", null, week).value().map { it.title }
        )
        assertEquals(emptyList<Any>(), source.search("standup", emptySet()).value())
        assertEquals(emptyList<Any>(), source.search("standup", setOf(CalendarId(1))).value())
    }

    @Test
    fun `search does not find a lone changed occurrence or a cancelled event`() = runTest {
        load(
            FeedIcs.event("c@x", "Gone", extra = "STATUS:CANCELLED\n"),
            FeedIcs.event("w@x", "Series", extra = "RRULE:FREQ=WEEKLY;COUNT=3\n") +
                "BEGIN:VEVENT\nUID:w@x\nDTSTAMP:20261001T000000Z\n" +
                "RECURRENCE-ID:20261013T100000Z\n" +
                "DTSTART:20261013T100000Z\nDTEND:20261013T110000Z\nSUMMARY:Moved\nEND:VEVENT\n"
        )

        assertEquals(emptyList<Any>(), source.search("moved").value())
        assertEquals(emptyList<Any>(), source.search("gone").value())
        assertEquals(listOf("Series"), source.search("series").value().map { it.title })
        assertEquals(listOf("Series", "Moved", "Series"), instances().map { it.title })
    }

    @Test
    fun `an event is read by its id, and ids of other sources are not found`() = runTest {
        val id = load(FeedIcs.event("a@x", "A", extra = "LOCATION:Cafe\n"))
        val row = rig.events.inSubscription(id).single()
        val eventId = SubscriptionIds.event(row.id)

        val event = source.event(eventId).value()

        assertEquals(eventId, event.id)
        assertEquals(SubscriptionIds.calendar(id), event.calendarId)
        assertEquals("Cafe", event.location)
        assertEquals(CalendarResult.Failure(CalendarError.NotFound), source.event(EventId(row.id)))
        assertEquals(
            CalendarResult.Failure(CalendarError.NotFound),
            source.event(SubscriptionIds.event(row.id + 100))
        )
    }

    @Test
    fun `every change is refused as read only and nothing is stored`() = runTest {
        val id = load(FeedIcs.event("a@x", "A", extra = "RRULE:FREQ=WEEKLY;COUNT=3\n"))
        val eventId = SubscriptionIds.event(rig.events.inSubscription(id).single().id)
        val event = source.event(eventId).value()
        val draft = EventDraft(
            SubscriptionIds.calendar(id),
            "New",
            EventTime.Timed(
                Instant.parse("2026-10-08T10:00:00Z"),
                Instant.parse("2026-10-08T11:00:00Z"),
                madrid
            )
        )
        val readOnly = CalendarResult.Failure(CalendarError.ReadOnly)
        val before = rig.events.inSubscription(id)

        assertEquals(readOnly, source.create(draft))
        assertEquals(readOnly, source.update(event.copy(title = "Hacked")))
        assertEquals(readOnly, source.delete(eventId))
        assertEquals(
            readOnly,
            source.editInstance(eventId, Instant.parse("2026-10-13T10:00:00Z"), draft)
        )
        assertEquals(
            readOnly,
            source.cancelInstance(eventId, Instant.parse("2026-10-13T10:00:00Z"))
        )
        assertEquals(readOnly, source.respond(eventId, AttendeeStatus.ACCEPTED))

        assertEquals(before, rig.events.inSubscription(id))
        assertEquals("A", source.event(eventId).value().title)
    }

    @Test
    fun `changes are announced when a download stores events`() = runBlocking {
        val id = rig.addRow()
        server.enqueue(
            MockResponse(
                200,
                okhttp3.Headers.headersOf(),
                FeedIcs.calendar(FeedIcs.event("a@x", "A"))
            )
        )

        val seen = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            withTimeout(CHANGE_TIMEOUT_MS) { source.changes.first() }
        }
        // Room's invalidation subscribes asynchronously; give it time.
        delay(SUBSCRIBE_DELAY_MS)
        rig.refresher.refresh(id)

        assertEquals(Unit, seen.await())
    }

    @Test
    fun `in the composite the ids never meet those of the phone's calendars`() = runTest {
        val phone = CalendarInfo(
            CalendarId(1),
            CalendarAccount("me@example.com", "LOCAL"),
            "Phone",
            0xFF0B63CE.toInt(),
            CalendarAccess.OWNER,
            ownerEmail = "me@example.com"
        )
        val provider = FakeCalendarSource(listOf(phone))
        val composite = CompositeCalendarSource(provider, FakeCalendarSource(emptyList()), source)
        val feed = load(FeedIcs.event("a@x", "From the feed"))
        provider.create(
            EventDraft(
                phone.id,
                "From the phone",
                EventTime.Timed(
                    Instant.parse("2026-10-06T12:00:00Z"),
                    Instant.parse("2026-10-06T13:00:00Z"),
                    madrid
                )
            )
        )

        val all = composite.instances(month).value()
        val calendars = composite.calendars().value()

        assertEquals(listOf("From the feed", "From the phone"), all.map { it.title })
        assertEquals(
            setOf(phone.id, SubscriptionIds.calendar(feed)),
            calendars.map { it.id }.toSet()
        )
        val feedEvent = all.first { it.title == "From the feed" }
        assertEquals(
            CalendarResult.Failure(CalendarError.ReadOnly),
            composite.delete(feedEvent.eventId)
        )
        val phoneEvent = all.first { it.title == "From the phone" }
        assertEquals(CalendarResult.Success(Unit), composite.delete(phoneEvent.eventId))
        assertEquals(listOf("From the feed"), composite.instances(month).value().map { it.title })
        assertEquals(
            listOf("From the feed"),
            composite.instances(month, setOf(SubscriptionIds.calendar(feed))).value().map {
                it.title
            }
        )
    }

    private companion object {
        const val CHANGE_TIMEOUT_MS = 5_000L
        const val SUBSCRIBE_DELAY_MS = 100L
    }
}
