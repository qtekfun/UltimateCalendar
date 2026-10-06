// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.caldav

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.sync.conflict.event
import com.qtekfun.ultimatecalendar.sync.engine.SyncOutcome
import com.qtekfun.ultimatecalendar.sync.queue.ProcessResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The CalDAV source with its sync, end to end against a fake server: offline writes, conflicts,
 * changes from the server, answers, scheduling, restarts.
 */
class CalDavSyncFlowsTest {
    @StartStop
    val server = MockWebServer()

    private lateinit var rig: CalDavRig
    private var mineId = CalendarId(0)

    private val madrid = ZoneId.of("Europe/Madrid")
    private val start = Instant.parse("2026-10-05T07:00:00Z")
    private val day = TimeRange(
        Instant.parse("2026-10-05T00:00:00Z"),
        Instant.parse("2026-10-06T00:00:00Z")
    )
    private val month = TimeRange(
        Instant.parse("2026-10-01T00:00:00Z"),
        Instant.parse("2026-11-01T00:00:00Z")
    )

    private fun setUp(
        configure: com.qtekfun.ultimatecalendar.sync.engine.FakeCalDav.() -> Unit = {
        }
    ) = runBlocking {
        rig = CalDavRig(server).setUp(configure)
        mineId = rig.calendars().first { it.displayName == "Mine" }.id
    }

    @AfterEach
    fun close() = rig.close()

    private fun draft(
        title: String = "Standup",
        attendees: List<Attendee> = emptyList(),
        rrule: String? = null,
        time: EventTime = EventTime.Timed(start, start.plusSeconds(1800), madrid)
    ) = EventDraft(mineId, title, time, rrule = rrule, attendees = attendees)

    private fun <T> CalendarResult<T>.ok(): T = when (this) {
        is CalendarResult.Success -> value
        is CalendarResult.Failure -> throw AssertionError("Expected success but got $error")
    }

    private suspend fun instances(range: TimeRange = month): List<EventInstance> =
        rig.source.instances(range).ok()

    /** The unfolded text of the only resource of "Mine" on the server. */
    private fun onServer(): String = rig.fake.resources.entries.single {
        it.key.startsWith(rig.mine)
    }
        .value.ics.replace(Regex("\r?\n[ \t]"), "")

    private val invitation = event(
        title = "Planning",
        organizer = "boss@example.com",
        attendees = listOf(
            Attendee.of(
                "boss@example.com",
                "Boss",
                status = AttendeeStatus.ACCEPTED,
                isOrganizer = true
            ),
            Attendee.of("me@example.com", "Me", status = AttendeeStatus.NEEDS_ACTION)
        )
    )

    @Test
    fun `a change made offline is queued, shown at once and sent by the next sync`() = runBlocking {
        setUp()
        rig.fake.failOn = { method, _ -> if (method == "PUT") SERVICE_UNAVAILABLE else null }

        val id = rig.source.create(draft()).ok()
        val first = rig.sync()

        assertEquals(SyncOutcome.Ok(ProcessResult(retried = 1)), first)
        assertEquals(listOf("Standup"), instances().map { it.title })
        assertEquals(1, rig.syncRequests)
        assertTrue(rig.fake.resources.isEmpty())

        rig.fake.failOn = { _, _ -> null }
        rig.waitOutBackoff()
        assertEquals(SyncOutcome.Ok(ProcessResult(done = 1)), rig.sync())

        assertTrue("SUMMARY:Standup" in onServer())
        assertEquals("Standup", rig.source.event(id).ok().title)
        assertEquals(0, rig.env.operations().size)
    }

    @Test
    fun `reading never needs the network`() = runBlocking {
        setUp()
        rig.source.create(draft()).ok()
        rig.sync()
        val before = rig.fake.requests.size
        rig.fake.failOn = { _, _ -> SERVICE_UNAVAILABLE }

        assertEquals(1, instances().size)
        assertEquals(1, rig.source.search("standup").ok().size)
        assertEquals(3, rig.calendars().size)

        assertEquals(before, rig.fake.requests.size)
    }

    @Test
    fun `a change on the server meanwhile is merged when the upload meets a 412`() = runBlocking {
        setUp()
        val href = rig.mine + "standup.ics"
        rig.fake.put(href, rig.env.ics(event(title = "Standup")))
        rig.sync()
        val id = instances().single().eventId
        rig.source.update(rig.source.event(id).ok().copy(location = "Room 4")).ok()
        rig.fake.put(href, rig.env.ics(event(title = "Standup moved")))

        val first = rig.sync()
        rig.waitOutBackoff()
        val second = rig.sync()

        assertEquals(SyncOutcome.Ok(ProcessResult(retried = 1)), first)
        // The merge queued the merged event again, besides the upload that met the 412.
        assertEquals(SyncOutcome.Ok(ProcessResult(done = 2)), second)
        val text = onServer()
        assertTrue("SUMMARY:Standup moved" in text)
        assertTrue("LOCATION:Room 4" in text)
        val merged = rig.source.event(id).ok()
        assertEquals("Standup moved", merged.title)
        assertEquals("Room 4", merged.location)
    }

    @Test
    fun `a change made on the server reaches the changes flow and the instances`() = runBlocking {
        setUp()
        assertEquals(0, instances().size)

        rig.source.changes.test {
            // Room subscribes to its invalidations asynchronously.
            delay(SUBSCRIBE_MS)
            rig.fake.put(rig.mine + "retro.ics", rig.env.ics(event(title = "Retro")))
            rig.sync()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("Retro"), instances().map { it.title })
    }

    @Test
    fun `answering writes my own PARTSTAT and the server tells the organizer`() = runBlocking {
        setUp()
        rig.fake.put(rig.mine + "plan.ics", rig.env.ics(invitation))
        rig.sync()
        val seen = instances().single()
        assertEquals(AttendeeStatus.NEEDS_ACTION, seen.selfStatus)

        rig.source.respond(seen.eventId, AttendeeStatus.ACCEPTED).ok()

        assertEquals(AttendeeStatus.ACCEPTED, instances().single().selfStatus)
        assertEquals(SyncOutcome.Ok(ProcessResult(done = 1)), rig.sync())
        val mine = onServer().lines().first { "mailto:me@example.com" in it }
        assertTrue("PARTSTAT=ACCEPTED" in mine, mine)
        assertEquals(listOf("REPLY ACCEPTED"), rig.fake.schedulingLog)
        assertEquals(AttendeeStatus.ACCEPTED, instances().single().selfStatus)
    }

    @Test
    fun `answering the same way twice sends nothing more`() = runBlocking {
        setUp()
        rig.fake.put(rig.mine + "plan.ics", rig.env.ics(invitation))
        rig.sync()
        val id = instances().single().eventId
        rig.source.respond(id, AttendeeStatus.TENTATIVE).ok()
        rig.sync()
        val requests = rig.syncRequests

        rig.source.respond(id, AttendeeStatus.TENTATIVE).ok()

        assertEquals(requests, rig.syncRequests)
        assertEquals(0, rig.env.operations().size)
    }

    @Test
    fun `an event with guests makes the user the organizer and the server sends the invitations`() =
        runBlocking {
            setUp()

            rig.source.create(draft(attendees = listOf(Attendee.of("zoe@example.com", "Zoe")))).ok()
            rig.sync()

            val text = onServer()
            assertTrue("ORGANIZER" in text && "mailto:me@example.com" in text)
            assertEquals(listOf("INVITE zoe@example.com"), rig.fake.schedulingLog)
            assertTrue(rig.env.db.davAccountDao().get(rig.env.account.id)!!.scheduling)
        }

    @Test
    fun `a server without scheduling keeps guests as a plain list and answers still count`() =
        runBlocking {
            setUp { scheduling = false }
            assertFalse(rig.env.db.davAccountDao().get(rig.env.account.id)!!.scheduling)

            rig.source.create(draft(attendees = listOf(Attendee.of("zoe@example.com")))).ok()
            rig.fake.put(rig.mine + "plan.ics", rig.env.ics(invitation))
            rig.sync()
            val plan = instances().first { it.title == "Planning" }
            rig.source.respond(plan.eventId, AttendeeStatus.DECLINED).ok()
            rig.sync()

            assertTrue(rig.fake.schedulingLog.isEmpty())
            val standup = rig.fake.resources.values.first { "SUMMARY:Standup" in it.ics }.ics
                .replace(Regex("\r?\n[ \t]"), "")
            assertTrue("mailto:zoe@example.com" in standup)
            val answered = rig.fake.resources.values.first { "SUMMARY:Planning" in it.ics }.ics
                .replace(Regex("\r?\n[ \t]"), "")
            assertTrue("PARTSTAT=DECLINED" in answered.lines().first { "me@example.com" in it })
        }

    @Test
    fun `a server that does not tell who the user is has no organizer and no answers`() =
        runBlocking {
            setUp {
                scheduling = false
                addresses = emptyList()
            }

            val id = rig.source.create(
                draft(attendees = listOf(Attendee.of("zoe@example.com")))
            ).ok()
            rig.fake.put(rig.mine + "plan.ics", rig.env.ics(invitation))
            rig.sync()

            assertEquals(null, rig.source.event(id).ok().organizer)
            val plan = instances().first { it.title == "Planning" }
            assertEquals(null, plan.selfStatus)
            assertEquals(
                CalendarResult.Failure(CalendarError.Invalid("not an attendee")),
                rig.source.respond(plan.eventId, AttendeeStatus.ACCEPTED)
            )
        }

    @Test
    fun `a queued change survives a restart and reaches the server once`() = runBlocking {
        setUp()
        rig.fake.failOn = { method, _ -> if (method == "PUT") SERVICE_UNAVAILABLE else null }
        rig.source.create(draft()).ok()
        rig.sync()
        assertEquals(1, rig.env.operations().size)

        // The process dies: everything but the database is built again.
        val engine = rig.restartedEngine()
        rig.fake.failOn = { _, _ -> null }
        rig.waitOutBackoff()
        assertEquals(SyncOutcome.Ok(ProcessResult(done = 1)), engine.sync())
        assertEquals(SyncOutcome.Ok(ProcessResult()), engine.sync())

        assertEquals(1, rig.fake.resources.keys.count { it.startsWith(rig.mine) })
        assertEquals(0, rig.env.operations().size)
        assertEquals(listOf("Standup"), rig.restarted().instances(month).ok().map { it.title })
    }

    @Test
    fun `deleting an event the server never had sends nothing, deleting a synced one deletes it`() =
        runBlocking {
            setUp()
            val local = rig.source.create(draft("Never sent")).ok()
            rig.source.delete(local).ok()
            val synced = rig.source.create(draft("Sent")).ok()
            rig.sync()
            assertEquals(1, rig.fake.resources.size)

            rig.source.delete(synced).ok()
            assertEquals(0, instances().size)
            rig.sync()

            assertTrue(rig.fake.resources.isEmpty())
            assertEquals(CalendarResult.Failure(CalendarError.NotFound), rig.source.event(synced))
            assertEquals(0, rig.env.operations().size)
        }

    @Test
    fun `one occurrence changed or cancelled reaches the server as an exception`() = runBlocking {
        setUp()
        val id = rig.source.create(draft(rrule = "FREQ=DAILY;COUNT=4")).ok()
        rig.sync()
        val second = start.plusSeconds(DAY)
        val third = start.plusSeconds(2 * DAY)

        rig.source.editInstance(id, second, draft("Moved").copy(time = timedAt(second))).ok()
        rig.source.cancelInstance(id, third).ok()
        rig.sync()

        val text = onServer()
        assertTrue("SUMMARY:Moved" in text)
        assertTrue("STATUS:CANCELLED" in text)
        assertEquals(listOf("Standup", "Moved", "Standup"), instances().map { it.title })
        assertEquals(
            CalendarResult.Failure(CalendarError.NotFound),
            rig.source.cancelInstance(id, start.plusSeconds(20 * DAY))
        )
        assertEquals(
            CalendarResult.Failure(CalendarError.Invalid("not a series")),
            rig.source.cancelInstance(rig.source.create(draft("Single")).ok(), start)
        )
    }

    @Test
    fun `an all-day series is changed by its date`() = runBlocking {
        setUp()
        val days = EventTime.AllDay(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6))
        val id = rig.source.create(draft("Holiday", rrule = "FREQ=DAILY;COUNT=3", time = days)).ok()

        rig.source.cancelInstance(id, Instant.parse("2026-10-06T00:00:00Z")).ok()

        assertEquals(
            listOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7)),
            instances().map { (it.time as EventTime.AllDay).startDate }
        )
    }

    @Test
    fun `moving the first occurrence drops the old exceptions, other changes keep them`() =
        runBlocking {
            setUp()
            val id = rig.source.create(draft(rrule = "FREQ=DAILY;COUNT=3")).ok()
            rig.source.cancelInstance(id, start.plusSeconds(DAY)).ok()

            val series = rig.source.event(id).ok()
            rig.source.update(series.copy(title = "Renamed")).ok()
            assertEquals(2, instances().size)

            rig.source.update(series.copy(time = timedAt(start.plusSeconds(HOUR)))).ok()
            assertEquals(3, instances().size)
        }

    @Test
    fun `a rule the engine cannot read still shows the event once`() = runBlocking {
        setUp()
        rig.fake.put(
            rig.mine + "odd.ics",
            rig.env.ics(event(title = "Odd", rrule = "FREQ=WEEKLY;BYDAY=2MO"))
        )
        rig.sync()

        assertEquals(listOf("Odd"), instances(day).map { it.title })
    }

    @Test
    fun `a read-only calendar refuses every change, and unknown ids are not found`() = runBlocking {
        setUp()
        val href = rig.holidays + "day.ics"
        rig.fake.put(href, rig.env.ics(invitation))
        rig.sync()
        val id = instances().single().eventId

        val results = listOf(
            rig.source.delete(id),
            rig.source.update(rig.source.event(id).ok()),
            rig.source.respond(id, AttendeeStatus.ACCEPTED),
            rig.source.cancelInstance(id, start)
        )

        assertTrue(results.all { it == CalendarResult.Failure(CalendarError.ReadOnly) })
        assertEquals(CalendarResult.Failure(CalendarError.NotFound), rig.source.event(EventId(5)))
        assertEquals(
            CalendarResult.Failure(CalendarError.NotFound),
            rig.source.create(draft().copy(calendarId = CalendarId(1)))
        )
    }

    @Test
    fun `an event cannot move to another calendar`() = runBlocking {
        setUp()
        val id = rig.source.create(draft()).ok()
        val other = rig.calendars().first { it.displayName == "Holidays" }.id

        val result = rig.source.update(rig.source.event(id).ok().copy(calendarId = other))

        assertTrue(result is CalendarResult.Failure)
        val failure = (result as CalendarResult.Failure).error
        assertTrue(failure is CalendarError.Invalid || failure == CalendarError.ReadOnly)
    }

    @Test
    fun `colors stay on the device and cancelled events are hidden`() = runBlocking {
        setUp()
        val id = rig.source.create(draft().copy(color = RED)).ok()
        rig.fake.put(
            rig.mine + "gone.ics",
            rig.env.ics(event(title = "Gone")).replace("STATUS:CONFIRMED", "STATUS:CANCELLED")
        )
        rig.sync()

        assertEquals(RED, rig.source.event(id).ok().color)
        assertEquals(RED, instances().single().color)
        rig.source.update(rig.source.event(id).ok().copy(color = null)).ok()
        assertEquals(null, instances().single().color)
    }

    @Test
    fun `without an account there is nothing`() = runBlocking {
        setUp()
        rig.env.db.davAccountDao().delete(rig.env.account.id)

        assertEquals(emptyList<Any>(), rig.calendars())
        assertEquals(0, instances().size)
        assertEquals(0, rig.source.search("standup").ok().size)
        assertEquals(CalendarResult.Failure(CalendarError.NotFound), rig.source.create(draft()))
    }

    private fun timedAt(from: Instant) = EventTime.Timed(from, from.plusSeconds(1800), madrid)

    private companion object {
        const val SERVICE_UNAVAILABLE = 503
        const val SUBSCRIBE_MS = 300L
        const val DAY = 86_400L
        const val HOUR = 3_600L
        const val RED = 0xFFB3261E.toInt()
    }
}
