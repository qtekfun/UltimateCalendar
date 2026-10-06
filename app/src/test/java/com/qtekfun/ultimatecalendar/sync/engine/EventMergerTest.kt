// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavResource
import com.qtekfun.ultimatecalendar.domain.model.Availability
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceKey
import com.qtekfun.ultimatecalendar.domain.recurrence.OccurrenceOverride
import com.qtekfun.ultimatecalendar.sync.conflict.EventField
import com.qtekfun.ultimatecalendar.sync.conflict.event
import com.qtekfun.ultimatecalendar.sync.conflict.timed
import com.qtekfun.ultimatecalendar.sync.queue.QueuedOperation
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** What happens to the events the user changed when the server has news (SPEC §5). */
class EventMergerTest {
    @StartStop
    val server = MockWebServer()

    private lateinit var env: EngineFixtures

    @BeforeEach
    fun setUp() = runTest { env = EngineFixtures(server).setUp() }

    @AfterEach
    fun close() = env.db.close()

    private val href get() = env.work + "standup.ics"

    private val earlier get() = env.clock.instant().minus(Duration.ofMinutes(10))
    private val later get() = env.clock.instant().plus(Duration.ofMinutes(10))

    /** The server edits the event at [at]: its LAST-MODIFIED. */
    private suspend fun serverEdits(at: Instant, edited: IcsEvent) {
        env.clock.now = at
        env.fake.put(href, env.ics(edited))
        env.clock.now = Instant.parse("2026-10-01T10:00:00Z")
        env.pullAll()
    }

    @Test
    fun `the server and the user changed different fields, both changes are kept and sent`() =
        runTest {
            env.pulled(href, event(title = "Standup"))
            env.editLocally(href, EventField.TITLE) { it.master { copy(title = "Daily") } }

            serverEdits(later, event(title = "Standup", location = "Room 2"))

            val stored = env.row(href)
            assertEquals("Daily", stored.title)
            assertEquals("Room 2", stored.location)
            assertEquals(EventField.TITLE.bit, stored.dirtyFields)
            assertEquals("\"2\"", stored.etag)
            assertNull(stored.conflictTitle)
            assertEquals(listOf<QueuedOperation>(QueuedOperation.UpdateEvent), env.operations())
        }

    @Test
    fun `a field changed on both sides goes to the later change, the server's here`() = runTest {
        env.pulled(href)
        val localTime = timed("2026-10-05T08:00:00Z", "2026-10-05T08:30:00Z")
        env.editLocally(href, EventField.TIME, at = earlier) {
            it.master { copy(time = localTime) }
        }
        env.queue.discard(env.db.pendingOperationDao().all(env.account.id).single().id)

        serverEdits(later, event(time = timed("2026-10-05T09:00:00Z", "2026-10-05T09:30:00Z")))

        val stored = env.row(href)
        assertEquals(Instant.parse("2026-10-05T09:00:00Z").toEpochMilli(), stored.start)
        assertEquals(0, stored.dirtyFields)
        assertEquals(later.toEpochMilli(), stored.modifiedAt)
        assertEquals(emptyList<QueuedOperation>(), env.operations())
    }

    @Test
    fun `a field changed on both sides goes to the later change, the user's here`() = runTest {
        env.pulled(href)
        val localTime = timed("2026-10-05T08:00:00Z", "2026-10-05T08:30:00Z")
        val edited = env.editLocally(href, EventField.TIME, at = later) {
            it.master { copy(time = localTime) }
        }
        env.queue.discard(env.db.pendingOperationDao().all(env.account.id).single().id)

        serverEdits(
            earlier,
            event(time = timed("2026-10-05T09:00:00Z", "2026-10-05T09:30:00Z"))
        )

        val stored = env.row(href)
        assertEquals(Instant.parse("2026-10-05T08:00:00Z").toEpochMilli(), stored.start)
        assertEquals(EventField.TIME.bit, stored.dirtyFields)
        assertEquals(edited.modifiedAt, stored.modifiedAt)
        assertEquals("\"2\"", stored.etag)
        assertEquals(listOf<QueuedOperation>(QueuedOperation.UpdateEvent), env.operations())
    }

    @Test
    fun `a text changed on both sides is kept here, the server's text waits for the user`() =
        runTest {
            env.pulled(href, event(title = "Standup", description = "Old", location = "Room 1"))
            env.editLocally(href, EventField.TITLE, EventField.DESCRIPTION, EventField.LOCATION) {
                it.master { copy(title = "Daily", description = "Mine", location = null) }
            }
            env.queue.discard(env.db.pendingOperationDao().all(env.account.id).single().id)

            serverEdits(
                earlier,
                event(title = "Planning", description = null, location = "Room 2")
            )

            val stored = env.row(href)
            assertEquals("Daily", stored.title)
            assertEquals("Planning", stored.conflictTitle)
            assertEquals("Mine", stored.description)
            // The server emptied it: that is still a conflict, told by an empty text.
            assertEquals("", stored.conflictDescription)
            assertNull(stored.location)
            assertEquals("Room 2", stored.conflictLocation)
            assertEquals(
                EventField.toBits(
                    setOf(EventField.TITLE, EventField.DESCRIPTION, EventField.LOCATION)
                ),
                stored.dirtyFields
            )
            // Nothing was won here that still has to be sent.
            assertEquals(emptyList<QueuedOperation>(), env.operations())
        }

    @Test
    fun `occurrences changed on each side are merged one by one`() = runTest {
        val monday = OccurrenceKey.Moment(Instant.parse("2026-10-05T07:00:00Z"))
        val tuesday = OccurrenceKey.Moment(Instant.parse("2026-10-06T07:00:00Z"))
        val series = event(rrule = "FREQ=DAILY")
        env.pulled(href, series)
        env.editLocally(href, EventField.OVERRIDES) {
            it.copy(series = it.series.copy(overrides = listOf(OccurrenceOverride(monday, null))))
        }
        val moved = series.series.event.copy(title = "Late start")

        serverEdits(
            later,
            series.copy(
                series = series.series.copy(overrides = listOf(OccurrenceOverride(tuesday, moved)))
            )
        )

        val overrides = env.series(href).series.overrides
        assertEquals(listOf(tuesday, monday), overrides.map { it.recurrenceId })
        assertEquals("Late start", overrides.first().replacement?.title)
        assertNull(overrides.last().replacement)
        assertEquals(EventField.OVERRIDES.bit, env.row(href).dirtyFields)
    }

    @Test
    fun `an event changed here that the server deleted waits for the user`() = runTest {
        env.pulled(href)
        env.editLocally(href, EventField.TITLE) { it.master { copy(title = "Daily") } }

        env.fake.remove(href)
        env.pullAll()

        val stored = env.row(href)
        assertTrue(stored.deletedOnServer)
        assertEquals("Daily", stored.title)
        assertNull(stored.etag)
        assertNull(stored.ics)
        assertEquals(emptyList<QueuedOperation>(), env.operations())
    }

    @Test
    fun `an event deleted on the server that was not changed here is deleted here`() = runTest {
        env.pulled(href)

        env.fake.remove(href)
        env.pullAll()

        assertNull(env.events.byHref(env.account.id, href))
    }

    @Test
    fun `an event deleted here stays so whatever the server changed, and its queue goes with it`() =
        runTest {
            env.pulled(href)
            val deleted = env.row(href).copy(deleted = true)
            env.events.update(deleted)
            env.queue.enqueue(env.account.id, deleted.id, QueuedOperation.DeleteEvent(href))

            serverEdits(later, event(title = "Changed meanwhile"))
            assertTrue(env.row(href).deleted)
            assertEquals("Standup", env.row(href).title)

            env.fake.remove(href)
            env.pullAll()

            assertNull(env.events.byHref(env.account.id, href))
            assertEquals(emptyList<QueuedOperation>(), env.operations())
        }

    @Test
    fun `news about something never seen here is ignored when it has no event`() = runTest {
        env.pullAll()
        env.merger.gone(env.account.id, "/not/here.ics")
        env.merger.apply(
            env.account.id,
            env.calendar(),
            DavResource("/not/here.ics", "\"1\"", null)
        )

        assertNull(env.events.byHref(env.account.id, "/not/here.ics"))
    }

    @Test
    fun `what the server changed is taken as it is when nothing was changed here`() = runTest {
        env.pulled(href)

        serverEdits(
            later,
            event(
                availability = Availability.FREE,
                time = timed("2026-10-05T07:00:00Z", "2026-10-05T07:45:00Z")
            )
        )

        val stored = env.row(href)
        assertEquals(Availability.FREE, stored.availability)
        assertEquals(Instant.parse("2026-10-05T07:45:00Z").toEpochMilli(), stored.end)
        assertEquals(0, stored.dirtyFields)
        assertEquals(later.toEpochMilli(), stored.modifiedAt)
    }
}
