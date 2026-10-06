// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderGateway
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderOp
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderQuery
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderRow
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderTable
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * How the provider source searches: the SQL it sends (always bound arguments, never the user's
 * text), what it reads in batches and what it keeps. What the real provider does with it is the
 * job of the contract suite on an emulator.
 */
class ProviderCalendarSearchTest {
    private val noon = Instant.parse("2026-10-06T10:00:00Z")
    private val gateway = RecordingGateway()
    private val source = ProviderCalendarSource(gateway, Dispatchers.Unconfined)

    private fun eventRow(id: Long, title: String, vararg extra: Pair<String, Any?>) = mapOf(
        Events._ID to id,
        Events.CALENDAR_ID to 1L,
        Events.TITLE to title,
        Events.DTSTART to noon.toEpochMilli(),
        Events.DTEND to noon.plusSeconds(3_600).toEpochMilli(),
        Events.EVENT_TIMEZONE to "Europe/Madrid",
        Events.ALL_DAY to 0L
    ) + extra

    private fun attendeeRow(eventId: Long, email: String, name: String? = null) = mapOf(
        Attendees.EVENT_ID to eventId,
        Attendees._ID to eventId * 10,
        Attendees.ATTENDEE_EMAIL to email,
        Attendees.ATTENDEE_NAME to name
    )

    private suspend fun search(
        query: String,
        calendars: Set<CalendarId>? = null,
        range: TimeRange? = null
    ) = (source.search(query, calendars, range) as CalendarResult.Success).value

    private val eventQueries get() = gateway.queries.filter { it.table == ProviderTable.EVENTS }

    @Test
    fun `an event is looked for with bound arguments in its title, place and description`() =
        runTest {
            gateway.textHits = listOf(eventRow(1, "Budget review"))

            val found = search("budget")

            assertEquals(listOf(EventId(1)), found.map { it.eventId })
            val query = eventQueries.first()
            val like = "LIKE ? ESCAPE '\\'"
            assertTrue(query.selection!!.contains("${Events.TITLE} $like"))
            assertTrue(query.selection.contains("${Events.EVENT_LOCATION} $like"))
            assertTrue(query.selection.contains("${Events.DESCRIPTION} $like"))
            assertEquals(List(3) { "%b_____%" }, query.args)
        }

    @Test
    fun `what the user typed never becomes part of the SQL`() = runTest {
        val hostile = "x'; DROP TABLE Events;-- 100%_\\"

        search(hostile)

        gateway.queries.forEach { query ->
            val sql = query.selection.orEmpty()
            assertFalse(sql.contains("DROP"), sql)
            assertFalse(sql.contains("100"), sql)
            assertEquals(sql.count { it == '?' }, query.args.size, sql)
        }
        // The most telling word travels escaped: "100%_\" keeps its percent, underscore and slash.
        assertTrue(eventQueries.first().args.all { it == "%100\\%\\_\\\\%" }) {
            eventQueries.first().args.toString()
        }
    }

    @Test
    fun `deleted, cancelled and single changed occurrences are left out by the query`() = runTest {
        search("budget")

        val sql = eventQueries.first().selection!!
        assertTrue(sql.contains("${Events.DELETED}=0"))
        assertTrue(sql.contains("${Events.ORIGINAL_ID} IS NULL"))
        assertTrue(sql.contains("${Events.STATUS}!=${Events.STATUS_CANCELED}"))
    }

    @Test
    fun `calendars are bound too, and an empty set asks nothing`() = runTest {
        search("budget", setOf(CalendarId(3), CalendarId(5)))

        val query = eventQueries.first()
        assertTrue(query.selection!!.contains("${Events.CALENDAR_ID} IN (?,?)"))
        assertEquals(listOf("3", "5"), query.args.take(2))

        gateway.queries.clear()
        assertEquals(emptyList<Any>(), search("budget", emptySet()))
        assertEquals(emptyList<Any>(), search("   "))
        assertTrue(gateway.queries.isEmpty())
    }

    @Test
    fun `an event found only through an attendee is read by id and not twice`() = runTest {
        gateway.textHits = listOf(eventRow(1, "Zoe lunch"))
        gateway.attendeeHits =
            listOf(attendeeRow(1, "zoe@example.com"), attendeeRow(2, "zoe@x.org"))
        gateway.events = listOf(eventRow(2, "Other"))
        gateway.attendees = listOf(attendeeRow(2, "zoe@x.org", "Zoe Other"))

        val found = search("zoe")

        assertEquals(setOf(EventId(1), EventId(2)), found.map { it.eventId }.toSet())
        val byId = eventQueries.last()
        assertTrue(byId.selection!!.contains("${Events._ID} IN (?)"))
        assertEquals(listOf("2"), byId.args)
        assertEquals("Zoe Other", found.single { it.eventId == EventId(2) }.attendees.single().name)
        val attendeeQuery = gateway.queries.first { it.table == ProviderTable.ATTENDEES }
        assertTrue(attendeeQuery.selection!!.contains("${Attendees.ATTENDEE_NAME} LIKE ?"))
        assertTrue(attendeeQuery.selection.contains("${Attendees.ATTENDEE_EMAIL} LIKE ?"))
    }

    @Test
    fun `long lists of ids are read in batches that stay under SQLite's limit`() = runTest {
        gateway.attendeeHits = (1L..850L).map { attendeeRow(it, "zoe@example.com") }
        gateway.events = (1L..850L).map { eventRow(it, "Event $it") }
        gateway.attendees = gateway.attendeeHits

        val found = search("zoe")

        assertEquals(850, found.size)
        val limit = 999
        assertTrue(gateway.queries.all { it.args.size < limit })
        val byId = eventQueries.filter { it.selection!!.contains("${Events._ID} IN") }
        assertEquals(listOf(400, 400, 50), byId.map { it.args.size })
    }

    @Test
    fun `a row the coarse filter let through but that does not match is dropped`() = runTest {
        gateway.textHits = listOf(eventRow(1, "Plan_B"), eventRow(2, "PlanXB"))

        assertEquals(listOf(EventId(1)), search("plan_b").map { it.eventId })
    }

    @Test
    fun `a row without a start or deleted by the user is not a result`() = runTest {
        gateway.textHits = listOf(
            eventRow(1, "Budget").minus(Events.DTSTART),
            eventRow(2, "Budget", Events.DELETED to 1L),
            eventRow(3, "Budget")
        )

        assertEquals(listOf(EventId(3)), search("budget").map { it.eventId })
    }

    @Test
    fun `with a range only the events that have an occurrence in it are kept`() = runTest {
        gateway.textHits = listOf(
            eventRow(1, "Gym", Events.RRULE to "FREQ=DAILY;COUNT=3"),
            eventRow(2, "Gym"),
            eventRow(3, "Gym")
        )
        gateway.instances = listOf(
            instanceRow(eventId = 1, begin = noon),
            // A changed occurrence is reported under its series.
            instanceRow(eventId = 77, begin = noon, originalId = 2),
            instanceRow(eventId = 9, begin = noon)
        )
        val range = TimeRange(noon.minusSeconds(60), noon.plusSeconds(60))

        val found = search("gym", range = range)

        assertEquals(listOf(EventId(1), EventId(2)), found.map { it.eventId })
        assertEquals(
            range.start.toEpochMilli()..range.end.toEpochMilli(),
            gateway.queries.single { it.table == ProviderTable.INSTANCES }.rangeMs
        )
    }

    @Test
    fun `a missing permission or a failing provider is reported, not thrown`() = runTest {
        gateway.failure = SecurityException("no permission")
        assertEquals(
            CalendarResult.Failure(CalendarError.PermissionDenied),
            source.search("budget")
        )
    }

    private fun instanceRow(eventId: Long, begin: Instant, originalId: Long? = null) = mapOf(
        Instances.EVENT_ID to eventId,
        Instances.ORIGINAL_ID to originalId,
        Instances.CALENDAR_ID to 1L,
        Instances.BEGIN to begin.toEpochMilli(),
        Instances.END to begin.plusSeconds(3_600).toEpochMilli(),
        Instances.ALL_DAY to 0L,
        Instances.EVENT_TIMEZONE to "Europe/Madrid"
    )

    /** Answers by table and by the kind of question the search asks. */
    private class RecordingGateway : ProviderGateway {
        var textHits: List<ProviderRow> = emptyList()
        var events: List<ProviderRow> = emptyList()
        var attendeeHits: List<ProviderRow> = emptyList()
        var attendees: List<ProviderRow> = emptyList()
        var instances: List<ProviderRow> = emptyList()
        var failure: Exception? = null
        val queries = mutableListOf<ProviderQuery>()

        override val changes: Flow<Unit> = emptyFlow()

        override fun query(query: ProviderQuery): List<ProviderRow> {
            failure?.let { throw it }
            queries += query
            val selection = query.selection.orEmpty()
            return when (query.table) {
                ProviderTable.EVENTS ->
                    if ("${Events._ID} IN" in selection) {
                        events.filter { (it[Events._ID] as Long).toString() in query.args }
                    } else {
                        textHits
                    }

                ProviderTable.ATTENDEES ->
                    if ("${Attendees.ATTENDEE_NAME} LIKE" in selection) {
                        attendeeHits
                    } else {
                        attendees.filter {
                            (it[Attendees.EVENT_ID] as Long).toString() in query.args
                        }
                    }

                ProviderTable.INSTANCES -> instances

                else -> emptyList()
            }
        }

        override fun apply(ops: List<ProviderOp>): List<Long?> = emptyList()
    }
}
