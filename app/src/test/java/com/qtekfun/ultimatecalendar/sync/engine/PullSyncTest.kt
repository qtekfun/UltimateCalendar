// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavResult
import com.qtekfun.ultimatecalendar.data.source.CalDavIds
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceOverride
import com.qtekfun.ultimatecalendar.sync.conflict.event
import java.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Pulls from [FakeCalDav] into the in-memory database, with the real merger and resolver. */
class PullSyncTest {
    @StartStop
    val server = MockWebServer()

    private lateinit var env: EngineFixtures
    private val fake get() = env.fake

    @BeforeEach
    fun setUp() = runTest { env = EngineFixtures(server).setUp() }

    @AfterEach
    fun close() = env.db.close()

    private val eventHref get() = env.work + "standup.ics"

    @Test
    fun `discovers the account, saves its calendars and downloads their events`() = runTest {
        val series = event(
            title = "Standup",
            location = "Room 1",
            rrule = "FREQ=DAILY;COUNT=3",
            attendees = listOf(Attendee.of("bo@example.com", status = AttendeeStatus.ACCEPTED)),
            organizer = "bo@example.com",
            reminders = listOf(Reminder(10)),
            overrides = listOf(
                OccurrenceOverride(
                    OccurrenceKey.Moment(Instant.parse("2026-10-06T07:00:00Z")),
                    null
                )
            )
        )
        fake.put(eventHref, env.ics(series))

        assertNull(env.pullAll())

        assertEquals(
            fake.home,
            requireNotNull(env.db.davAccountDao().get(env.account.id)).calendarHome
        )
        val calendar = env.calendar()
        assertEquals("Work", calendar.name)
        assertTrue(calendar.writable)
        assertNotNull(calendar.syncToken)
        val stored = env.row(eventHref)
        assertEquals(calendar.id, stored.calendarId)
        assertEquals("\"1\"", stored.etag)
        assertEquals(0, stored.dirtyFields)
        assertNotNull(stored.ics)
        val read = env.series(eventHref).series
        assertEquals("Standup", read.event.title)
        assertEquals("Room 1", read.event.location)
        assertEquals("FREQ=DAILY;COUNT=3", read.event.rrule)
        assertEquals(
            listOf(
                Attendee.of("bo@example.com", status = AttendeeStatus.ACCEPTED, isOrganizer = true)
            ),
            read.event.attendees
        )
        assertEquals("bo@example.com", read.event.organizer)
        assertEquals(listOf(Reminder(10)), read.event.reminders)
        assertEquals(
            series.series.overrides.map {
                it.recurrenceId
            },
            read.overrides.map { it.recurrenceId }
        )
        assertNull(read.overrides.single().replacement)
    }

    @Test
    fun `a second pull fetches nothing when nothing changed, and only what changed otherwise`() =
        runTest {
            fake.put(eventHref, env.ics(event(title = "Standup")))
            fake.put(env.work + "retro.ics", env.ics(event(title = "Retro")))
            env.pullAll()
            val before = fake.requests.size

            env.pullAll()
            // The calendar list and one sync-collection REPORT: no event is downloaded again.
            assertEquals(
                listOf("PROPFIND ${fake.home}", "REPORT ${env.work}"),
                fake.requests.drop(before)
            )

            fake.put(env.work + "retro.ics", env.ics(event(title = "Retrospective")))
            env.pullAll()

            assertEquals("Retrospective", env.row(env.work + "retro.ics").title)
            assertEquals("Standup", env.row(eventHref).title)
            assertEquals("\"1\"", env.row(eventHref).etag)
        }

    @Test
    fun `events deleted on the server are deleted here`() = runTest {
        env.pulled(eventHref)
        env.pulled(env.work + "retro.ics", event(title = "Retro"))
        fake.remove(eventHref)

        env.pullAll()

        assertNull(env.events.byHref(env.account.id, eventHref))
        assertNotNull(env.events.byHref(env.account.id, env.work + "retro.ics"))
    }

    @Test
    fun `a full pull removes what the server no longer lists, not what was never uploaded`() =
        runTest {
            env.pulled(eventHref)
            env.createLocally(env.work + "new.ics")
            // The sync token is lost: the next pull is a full one.
            env.calendars.setSyncToken(env.calendar().id, null)
            fake.resources.remove(eventHref)

            env.pullAll()

            assertNull(env.events.byHref(env.account.id, eventHref))
            assertNotNull(env.events.byHref(env.account.id, env.work + "new.ics"))
        }

    @Test
    fun `an expired sync token is replaced by a full pull`() = runTest {
        env.pulled(eventHref)
        env.calendars.setSyncToken(env.calendar().id, "from-another-era")
        fake.put(env.work + "retro.ics", env.ics(event(title = "Retro")))

        assertNull(env.pullAll())

        assertEquals("Retro", env.row(env.work + "retro.ics").title)
        assertNotEquals("from-another-era", env.calendar().syncToken)
    }

    @Test
    fun `calendars without sync tokens are read whole, and only when their ctag moved`() = runTest {
        fake.withoutSync += env.work
        fake.put(eventHref, env.ics(event(title = "Standup")))

        assertNull(env.pullAll())
        assertEquals("Standup", env.row(eventHref).title)
        assertNull(env.calendar().syncToken)
        val after = fake.requests.size

        assertNull(env.pullAll())
        // Only the calendar list was asked for: the ctag did not move.
        assertEquals(1, fake.requests.size - after)

        fake.put(eventHref, env.ics(event(title = "Daily")))
        assertNull(env.pullAll())
        assertEquals("Daily", env.row(eventHref).title)
    }

    @Test
    fun `a calendar removed from the server goes with its events, unless one was not uploaded`() =
        runTest {
            val home = fake.addCalendar("Home")
            val other = fake.addCalendar("Other")
            fake.put(home + "a.ics", env.ics(event(title = "A")))
            fake.put(other + "b.ics", env.ics(event(title = "B")))
            env.pullAll()
            env.createLocally(other + "unsent.ics", calendarHref = other)
            fake.calendars.remove(home)
            fake.calendars.remove(other)

            env.pullAll()

            assertNull(env.calendars.byHref(env.account.id, home))
            assertNotNull(env.calendars.byHref(env.account.id, other))
            assertNull(env.events.byHref(env.account.id, home + "a.ics"))
        }

    @Test
    fun `an event edited on the server replaces the one here when nothing changed here`() =
        runTest {
            env.pulled(eventHref, event(title = "Standup", rrule = "FREQ=DAILY"))
            fake.put(eventHref, env.ics(event(title = "Standup 2", rrule = "FREQ=WEEKLY")))

            env.pullAll()

            val stored = env.row(eventHref)
            assertEquals("Standup 2", stored.title)
            assertEquals("FREQ=WEEKLY", stored.rrule)
            assertEquals("\"2\"", stored.etag)
        }

    @Test
    fun `resources without data or without an event are ignored`() = runTest {
        fake.put(eventHref, "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n")
        fake.put(env.work + "empty.ics", "")

        assertNull(env.pullAll())

        assertNull(env.events.byHref(env.account.id, eventHref))
        assertNull(env.events.byHref(env.account.id, env.work + "empty.ics"))
    }

    @Test
    fun `floating times are read in the clock's zone, through the end of daylight saving`() =
        runTest {
            fun floating(uid: String, day: String) =
                "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:$uid\r\n" +
                    "DTSTAMP:20261001T100000Z\r\nDTSTART:${day}T090000\r\nDTEND:${day}T100000\r\n" +
                    "SUMMARY:$uid\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
            fake.put(env.work + "before.ics", floating("before", "20261024"))
            fake.put(env.work + "after.ics", floating("after", "20261026"))

            env.pullAll()

            // 09:00 in Madrid is 07:00Z before the clocks go back on 2026-10-25, 08:00Z after.
            val before = env.series(env.work + "before.ics").series.event.time as EventTime.Timed
            val after = env.series(env.work + "after.ics").series.event.time as EventTime.Timed
            assertEquals(Instant.parse("2026-10-24T07:00:00Z"), before.start)
            assertEquals(Instant.parse("2026-10-26T08:00:00Z"), after.start)
            assertEquals("Europe/Madrid", env.row(env.work + "after.ics").zone)
        }

    @Test
    fun `an unauthorized account stops the pull with that failure`() = runTest {
        fake.failures["/remote.php/dav/"] = ArrayDeque(listOf(401))

        assertEquals(DavResult.Unauthorized, env.pullAll())
    }

    @Test
    fun `a failing calendar list or event download is returned and nothing is half saved`() =
        runTest {
            fake.put(eventHref, env.ics(event(title = "Standup")))
            fake.failures[fake.home] = ArrayDeque(listOf(500))
            assertEquals(DavResult.HttpError(500), env.pullAll())

            fake.failures[env.work] = ArrayDeque(listOf(503))
            assertEquals(DavResult.HttpError(503), env.pullAll())
            assertNull(env.calendar().syncToken)
            assertNull(env.events.byHref(env.account.id, eventHref))

            assertNull(env.pullAll())
            assertEquals("Standup", env.row(eventHref).title)
        }

    @Test
    fun `an event download that fails after the change list leaves the calendar as it was`() =
        runTest {
            fake.put(eventHref, env.ics(event(title = "Standup")))
            // The first REPORT (the change list) works, the second (the multiget) does not.
            var reports = 0
            fake.failOn = { method, _ -> if (method == "REPORT" && ++reports == 2) 500 else null }

            assertEquals(DavResult.HttpError(500), env.pullAll())

            assertNull(env.events.byHref(env.account.id, eventHref))
            assertNull(env.calendar().syncToken)
        }

    @Test
    fun `a calendar the server shares read-only is not writable here`() = runTest {
        fake.readOnly += env.work

        env.pullAll()

        assertFalse(env.calendar().writable)
    }

    @Test
    fun `many events are downloaded in batches`() = runTest {
        (1..120).forEach { fake.put(env.work + "e$it.ics", env.ics(event(title = "E$it"))) }

        assertNull(env.pullAll())

        assertEquals(120, env.events.inCalendar(env.calendar().id).size)
        assertEquals("E120", env.row(env.work + "e120.ics").title)
    }

    @Test
    fun `a calendar switched off is not pulled, and is pulled again when switched on`() = runTest {
        env.pullAll()
        val calendar = env.calendar()
        val id = CalDavIds.calendar(calendar.id).value
        val settings = env.db.calendarSettingsDao()
        settings.save(CalendarSettingsEntity(id, null, null, false))
        // A provider calendar that happens to be off has nothing to do with this account.
        settings.save(CalendarSettingsEntity(3, null, null, false))
        fake.put(eventHref, env.ics(event(title = "Standup")))
        val requests = fake.requests.size

        assertNull(env.pullAll())

        assertNull(env.events.byHref(env.account.id, eventHref))
        // The calendars are still listed, but none of them was asked for its events.
        assertEquals(env.work, env.calendar().href)
        assertTrue(fake.requests.drop(requests).none { it.startsWith("REPORT") })

        settings.clear(id)
        assertNull(env.pullAll())
        assertEquals("Standup", env.row(eventHref).title)
    }
}
