// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source

import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderFailure
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderGateway
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderOp
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderQuery
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderRow
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderTable
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The provider source's decisions (access checks, what is written, how failures are reported)
 * against a scripted gateway. How the real provider behaves is the job of the contract suite on
 * an emulator.
 */
class ProviderCalendarSourceTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val noon = Instant.parse("2026-10-06T10:00:00Z")
    private val gateway = ScriptedGateway()
    private val source = ProviderCalendarSource(gateway, Dispatchers.Unconfined)

    private fun calendar(id: Long, level: Int, owner: String? = "me@example.com") = mapOf(
        Calendars._ID to id,
        Calendars.ACCOUNT_NAME to "tests@example.com",
        Calendars.ACCOUNT_TYPE to "LOCAL",
        Calendars.CALENDAR_DISPLAY_NAME to "Cal $id",
        Calendars.CALENDAR_ACCESS_LEVEL to level.toLong(),
        Calendars.VISIBLE to 1L,
        Calendars.OWNER_ACCOUNT to owner
    )

    private fun eventRow(id: Long, calendar: Long, vararg extra: Pair<String, Any?>) = mapOf(
        Events._ID to id,
        Events.CALENDAR_ID to calendar,
        Events.TITLE to "Lunch",
        Events.DTSTART to noon.toEpochMilli(),
        Events.DTEND to noon.plusSeconds(3_600).toEpochMilli(),
        Events.EVENT_TIMEZONE to "Europe/Madrid",
        Events.ALL_DAY to 0L
    ) + extra

    private fun instanceRow(
        id: Long,
        calendar: Long,
        begin: Instant,
        vararg extra: Pair<String, Any?>
    ) = mapOf(
        Instances.EVENT_ID to id,
        Instances.CALENDAR_ID to calendar,
        Instances.BEGIN to begin.toEpochMilli(),
        Instances.END to begin.plusSeconds(3_600).toEpochMilli(),
        Instances.TITLE to "T$id",
        Instances.ALL_DAY to 0L,
        Instances.EVENT_TIMEZONE to "Europe/Madrid"
    ) + extra

    private fun draft(calendar: Long = OWNED, rrule: String? = null) = EventDraft(
        CalendarId(calendar),
        "Lunch",
        EventTime.Timed(noon, noon.plusSeconds(3_600), madrid),
        rrule = rrule
    )

    private fun <T> CalendarResult<T>.error() = (this as CalendarResult.Failure).error

    private fun <T> CalendarResult<T>.value() = (this as CalendarResult.Success).value

    private fun applied() = gateway.applied.single()

    @Test
    fun `calendars are listed with their access`() = runTest {
        gateway.calendars = listOf(calendar(OWNED, 700), calendar(READ_ONLY, 200))

        val found = source.calendars().value()

        assertEquals(listOf(OWNED, READ_ONLY), found.map { it.id.value })
        assertEquals(listOf(true, false), found.map { it.access.canEdit })
    }

    @Test
    fun `a missing permission is reported as such by every operation`() = runTest {
        gateway.failure = SecurityException("no permission")
        val id = EventId(1)

        assertEquals(CalendarError.PermissionDenied, source.calendars().error())
        assertEquals(CalendarError.PermissionDenied, source.instances(range()).error())
        assertEquals(CalendarError.PermissionDenied, source.event(id).error())
        assertEquals(CalendarError.PermissionDenied, source.create(draft()).error())
        assertEquals(CalendarError.PermissionDenied, source.delete(id).error())
        assertEquals(
            CalendarError.PermissionDenied,
            source.respond(id, AttendeeStatus.ACCEPTED).error()
        )
    }

    @Test
    fun `a failing provider is a source failure and rejected data is invalid`() = runTest {
        gateway.failure = ProviderFailure("calendar provider unavailable")
        assertEquals(
            CalendarError.SourceFailure("calendar provider unavailable"),
            source.calendars().error()
        )

        gateway.failure = IllegalArgumentException("Unknown URL")
        assertEquals(
            CalendarError.Invalid("the provider rejected the data"),
            source.calendars().error()
        )
    }

    @Test
    fun `instances are filtered by calendar, sorted by start and cut to the range`() = runTest {
        gateway.instances = listOf(
            instanceRow(2, OWNED, noon.plusSeconds(7_200)),
            instanceRow(1, OWNED, noon),
            instanceRow(3, READ_ONLY, noon),
            instanceRow(4, OWNED, noon.minusSeconds(3_600 * 11)),
            instanceRow(
                5,
                OWNED,
                noon.plusSeconds(100),
                Instances.STATUS to Events.STATUS_CANCELED.toLong()
            )
        )

        val all = source.instances(range()).value()
        val own = source.instances(range(), setOf(CalendarId(OWNED))).value()

        assertEquals(listOf(1L, 3L, 2L), all.map { it.eventId.value })
        assertEquals(listOf(1L, 2L), own.map { it.eventId.value })
        assertEquals(
            noon.minusSeconds(3_600).toEpochMilli()..noon.plusSeconds(86_400).toEpochMilli(),
            gateway.queries.first { it.table == ProviderTable.INSTANCES }.rangeMs
        )
    }

    @Test
    fun `an event is read with its attendees and reminders and a deleted one is not found`() =
        runTest {
            gateway.events = listOf(
                eventRow(1, OWNED),
                eventRow(2, OWNED, Events.DELETED to 1L)
            )
            gateway.attendees = listOf(
                mapOf(
                    Attendees.EVENT_ID to 1L,
                    Attendees._ID to 10L,
                    Attendees.ATTENDEE_EMAIL to "ana@example.com"
                )
            )
            gateway.reminders = listOf(
                mapOf(Reminders.EVENT_ID to 1L, Reminders.MINUTES to 15L, Reminders.METHOD to 1L)
            )

            val event = source.event(EventId(1)).value()

            assertEquals(listOf("ana@example.com"), event.attendees.map { it.email })
            assertEquals(listOf(Reminder(15)), event.reminders)
            assertEquals(CalendarError.NotFound, source.event(EventId(2)).error())
            assertEquals(CalendarError.NotFound, source.event(EventId(3)).error())
        }

    @Test
    fun `create writes the event, then its attendees and reminders, in one batch`() = runTest {
        gateway.calendars = listOf(calendar(OWNED, 700))
        gateway.insertedIds = listOf(42L)
        val created = draft().copy(
            attendees = listOf(Attendee.of("ana@example.com")),
            reminders = listOf(Reminder(10))
        )

        val id = source.create(created).value()

        assertEquals(EventId(42), id)
        val ops = applied()
        assertEquals(3, ops.size)
        val event = ops[0] as ProviderOp.Insert
        assertEquals(ProviderTable.EVENTS, event.table)
        assertEquals(OWNED, event.values[Events.CALENDAR_ID])
        assertEquals("me@example.com", event.values[Events.ORGANIZER])
        assertEquals(
            listOf(ProviderTable.ATTENDEES, ProviderTable.REMINDERS),
            ops.drop(1).map {
                (it as ProviderOp.Insert).table
            }
        )
        assertEquals(listOf(0, 0), ops.drop(1).map { (it as ProviderOp.Insert).parentOp })
        assertTrue(
            ops.drop(1).none {
                (it as ProviderOp.Insert).values.containsKey(Attendees.EVENT_ID)
            }
        )
    }

    @Test
    fun `create refuses a read-only or unknown calendar and a missing id`() = runTest {
        gateway.calendars = listOf(calendar(OWNED, 700), calendar(READ_ONLY, 200))

        assertEquals(CalendarError.ReadOnly, source.create(draft(READ_ONLY)).error())
        assertEquals(CalendarError.NotFound, source.create(draft(99)).error())
        assertTrue(gateway.applied.isEmpty())

        gateway.insertedIds = listOf(null)
        assertEquals(CalendarError.SourceFailure("no event id"), source.create(draft()).error())
    }

    @Test
    fun `writes never touch sync columns or sync-adapter parameters`() = runTest {
        gateway.calendars = listOf(calendar(OWNED, 700))
        gateway.events = listOf(eventRow(1, OWNED, Events.RRULE to "FREQ=DAILY;COUNT=3"))
        gateway.instances = listOf(instanceRow(1, OWNED, noon))
        gateway.attendees = listOf(
            mapOf(
                Attendees.EVENT_ID to 1L,
                Attendees._ID to 10L,
                Attendees.ATTENDEE_EMAIL to "me@example.com"
            )
        )
        val full = draft().copy(
            attendees = listOf(Attendee.of("me@example.com")),
            reminders = listOf(Reminder(5))
        )

        source.create(full)
        source.update(
            source.event(EventId(1)).value().copy(title = "x", attendees = full.attendees)
        )
        source.editInstance(EventId(1), noon, full)
        source.cancelInstance(EventId(1), noon)
        source.respond(EventId(1), AttendeeStatus.DECLINED)
        source.delete(EventId(1))

        val forbidden = setOf(
            Events._SYNC_ID,
            Events.SYNC_DATA1,
            Events.SYNC_DATA2,
            Events.DIRTY,
            Events.CAL_SYNC1,
            Events.CAL_SYNC2,
            "caller_is_syncadapter"
        )
        val written = gateway.applied.flatten().flatMap {
            when (it) {
                is ProviderOp.Insert -> it.values.keys
                is ProviderOp.InsertException -> it.values.keys
                is ProviderOp.Update -> it.values.keys
                is ProviderOp.Delete -> emptySet()
            }
        }
        assertEquals(6, gateway.applied.size)
        assertTrue(written.none { it in forbidden })
    }

    @Test
    fun `update rewrites only the event when its attendees and reminders are unchanged`() =
        runTest {
            gateway.calendars = listOf(calendar(OWNED, 700))
            gateway.events = listOf(eventRow(1, OWNED))
            val stored = source.event(EventId(1)).value()

            source.update(stored.copy(title = "Dinner")).value()

            val ops = applied()
            assertEquals(1, ops.size)
            val update = ops.single() as ProviderOp.Update
            assertEquals(ProviderTable.EVENTS, update.table)
            assertEquals(1L, update.id)
            assertEquals("Dinner", update.values[Events.TITLE])
        }

    @Test
    fun `update replaces the attendees and reminders that changed and only those`() = runTest {
        gateway.calendars = listOf(calendar(OWNED, 700))
        gateway.events = listOf(eventRow(1, OWNED))
        val stored = source.event(EventId(1)).value()

        source.update(stored.copy(attendees = listOf(Attendee.of("ana@example.com")))).value()

        val ops = applied().drop(1)
        assertEquals(2, ops.size)
        assertEquals(ProviderTable.ATTENDEES, (ops[0] as ProviderOp.Delete).table)
        assertEquals(listOf("1"), (ops[0] as ProviderOp.Delete).args)
        val inserted = ops[1] as ProviderOp.Insert
        assertEquals(1L, inserted.values[Attendees.EVENT_ID])
        assertEquals(null, inserted.parentOp)

        gateway.applied.clear()
        source.update(stored.copy(reminders = listOf(Reminder(5)))).value()
        assertEquals(
            listOf(ProviderTable.REMINDERS, ProviderTable.REMINDERS),
            applied().drop(1).map {
                (it as? ProviderOp.Delete)?.table
                    ?: (it as ProviderOp.Insert).table
            }
        )
    }

    @Test
    fun `update and delete refuse what the calendar does not allow or does not have`() = runTest {
        gateway.calendars = listOf(calendar(OWNED, 700), calendar(READ_ONLY, 200))
        gateway.events = listOf(eventRow(1, OWNED), eventRow(2, READ_ONLY))
        val owned = source.event(EventId(1)).value()
        val foreign = source.event(EventId(2)).value()

        assertEquals(CalendarError.ReadOnly, source.update(foreign).error())
        assertEquals(CalendarError.ReadOnly, source.delete(EventId(2)).error())
        assertEquals(CalendarError.NotFound, source.update(owned.copy(id = EventId(7))).error())
        assertEquals(CalendarError.NotFound, source.delete(EventId(7)).error())
        assertTrue(gateway.applied.isEmpty())

        source.delete(EventId(1)).value()
        assertEquals(
            ProviderOp.Delete(ProviderTable.EVENTS, id = 1),
            applied().single()
        )
    }

    @Test
    fun `cancelling an occurrence inserts a cancelled exception for that start`() = runTest {
        gateway.calendars = listOf(calendar(OWNED, 700))
        gateway.events = listOf(eventRow(1, OWNED, Events.RRULE to "FREQ=DAILY;COUNT=3"))
        gateway.instances = listOf(instanceRow(1, OWNED, noon))

        source.cancelInstance(EventId(1), noon).value()

        assertEquals(
            ProviderOp.InsertException(
                1,
                mapOf(
                    Events.STATUS to Events.STATUS_CANCELED,
                    Events.ORIGINAL_INSTANCE_TIME to noon.toEpochMilli()
                )
            ),
            applied().single()
        )
    }

    @Test
    fun `editing an occurrence inserts an exception without a rule and with its children`() =
        runTest {
            gateway.calendars = listOf(calendar(OWNED, 700))
            gateway.events = listOf(eventRow(1, OWNED, Events.RRULE to "FREQ=DAILY;COUNT=3"))
            gateway.instances = listOf(instanceRow(1, OWNED, noon))
            val edit = draft(
                rrule = "FREQ=DAILY"
            ).copy(title = "Moved", reminders = listOf(Reminder(5)))

            source.editInstance(EventId(1), noon, edit).value()

            val ops = applied()
            val exception = ops[0] as ProviderOp.InsertException
            assertEquals(1L, exception.eventId)
            assertEquals("Moved", exception.values[Events.TITLE])
            assertFalse(
                exception.values.keys.any {
                    it in
                        setOf(Events.RRULE, Events.DTEND, Events.CALENDAR_ID)
                }
            )
            assertEquals("P3600S", exception.values[Events.DURATION])
            assertEquals(noon.toEpochMilli(), exception.values[Events.ORIGINAL_INSTANCE_TIME])
            assertEquals(Events.STATUS_CONFIRMED, exception.values[Events.STATUS])
            assertEquals(0, (ops[1] as ProviderOp.Insert).parentOp)
        }

    @Test
    fun `changing an occurrence again updates its exception instead of adding another`() = runTest {
        gateway.calendars = listOf(calendar(OWNED, 700))
        gateway.events = listOf(eventRow(1, OWNED, Events.RRULE to "FREQ=DAILY;COUNT=3"))
        gateway.exceptions = listOf(mapOf(Events._ID to 77L))

        source.editInstance(EventId(1), noon, draft().copy(title = "Again")).value()

        val ops = applied()
        val update = ops[0] as ProviderOp.Update
        assertEquals(77L, update.id)
        assertEquals("Again", update.values[Events.TITLE])
        assertEquals(
            listOf(ProviderTable.ATTENDEES, ProviderTable.REMINDERS),
            ops.drop(1).map {
                (it as ProviderOp.Delete).table
            }
        )

        gateway.applied.clear()
        source.cancelInstance(EventId(1), noon).value()
        assertEquals(
            ProviderOp.Update(
                ProviderTable.EVENTS,
                77,
                mapOf(
                    Events.STATUS to Events.STATUS_CANCELED,
                    Events.ORIGINAL_INSTANCE_TIME to noon.toEpochMilli()
                )
            ),
            applied().single()
        )
    }

    @Test
    fun `an occurrence that does not exist, or of something that is not a series, is refused`() =
        runTest {
            gateway.calendars = listOf(calendar(OWNED, 700), calendar(READ_ONLY, 200))
            gateway.events = listOf(
                eventRow(1, OWNED, Events.RRULE to "FREQ=DAILY;COUNT=3"),
                eventRow(2, OWNED),
                eventRow(3, READ_ONLY, Events.RRULE to "FREQ=DAILY")
            )
            gateway.instances =
                listOf(instanceRow(1, OWNED, noon.plusSeconds(1)), instanceRow(9, OWNED, noon))

            assertEquals(CalendarError.NotFound, source.cancelInstance(EventId(1), noon).error())
            assertEquals(
                CalendarError.Invalid("not a series"),
                source.cancelInstance(EventId(2), noon).error()
            )
            assertEquals(CalendarError.ReadOnly, source.cancelInstance(EventId(3), noon).error())
            assertEquals(CalendarError.NotFound, source.cancelInstance(EventId(8), noon).error())
            assertTrue(gateway.applied.isEmpty())
        }

    @Test
    fun `responding writes the answer to my attendee row`() = runTest {
        gateway.calendars = listOf(calendar(OWNED, 700))
        gateway.events = listOf(eventRow(1, OWNED))
        gateway.attendees = listOf(
            mapOf(
                Attendees.EVENT_ID to 1L,
                Attendees._ID to 10L,
                Attendees.ATTENDEE_EMAIL to "ana@example.com"
            ),
            mapOf(
                Attendees.EVENT_ID to 1L,
                Attendees._ID to 11L,
                Attendees.ATTENDEE_EMAIL to "Me@Example.com"
            )
        )

        source.respond(EventId(1), AttendeeStatus.TENTATIVE).value()

        assertEquals(
            ProviderOp.Update(
                ProviderTable.ATTENDEES,
                11,
                mapOf(Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_TENTATIVE)
            ),
            applied().single()
        )
    }

    @Test
    fun `responding finds my row by an alias or by the account name`() = runTest {
        gateway.calendars = listOf(calendar(OWNED, 700, owner = null))
        gateway.events = listOf(eventRow(1, OWNED))
        gateway.attendees = listOf(
            mapOf(
                Attendees.EVENT_ID to 1L,
                Attendees._ID to 10L,
                Attendees.ATTENDEE_EMAIL to "Alias@Example.com"
            ),
            mapOf(
                Attendees.EVENT_ID to 1L,
                Attendees._ID to 11L,
                Attendees.ATTENDEE_EMAIL to "tests@example.com"
            )
        )
        val withAlias = ProviderCalendarSource(gateway, Dispatchers.Unconfined) {
            setOf("alias@example.com")
        }

        withAlias.respond(EventId(1), AttendeeStatus.ACCEPTED).value()
        assertEquals(10L, (applied().single() as ProviderOp.Update).id)

        // Without the alias the account name (an address) still finds the row.
        gateway.applied.clear()
        source.respond(EventId(1), AttendeeStatus.ACCEPTED).value()
        assertEquals(11L, (applied().single() as ProviderOp.Update).id)
    }

    @Test
    fun `responding needs access, an attendee row and an owner address`() = runTest {
        gateway.calendars = listOf(
            calendar(OWNED, 700),
            calendar(READ_ONLY, 200),
            calendar(NO_OWNER, 700, owner = null)
        )
        gateway.events = listOf(eventRow(1, OWNED), eventRow(2, READ_ONLY), eventRow(3, NO_OWNER))
        gateway.attendees = listOf(
            mapOf(
                Attendees.EVENT_ID to 1L,
                Attendees._ID to 10L,
                Attendees.ATTENDEE_EMAIL to "ana@example.com"
            ),
            mapOf(
                Attendees.EVENT_ID to 3L,
                Attendees._ID to 12L,
                Attendees.ATTENDEE_EMAIL to "me@example.com"
            )
        )

        assertEquals(
            CalendarError.Invalid("not an attendee"),
            source.respond(EventId(1), AttendeeStatus.ACCEPTED).error()
        )
        assertEquals(
            CalendarError.ReadOnly,
            source.respond(EventId(2), AttendeeStatus.ACCEPTED).error()
        )
        assertEquals(
            CalendarError.Invalid("not an attendee"),
            source.respond(EventId(3), AttendeeStatus.ACCEPTED).error()
        )
        assertEquals(
            CalendarError.NotFound,
            source.respond(EventId(5), AttendeeStatus.ACCEPTED).error()
        )
        assertTrue(gateway.applied.isEmpty())
    }

    @Test
    fun `changes come from the provider observer`() = runTest {
        source.changes.test {
            gateway.changed.emit(Unit)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun range() = TimeRange(noon.minusSeconds(3_600), noon.plusSeconds(86_400))

    private class ScriptedGateway : ProviderGateway {
        var calendars: List<ProviderRow> = emptyList()
        var events: List<ProviderRow> = emptyList()
        var exceptions: List<ProviderRow> = emptyList()
        var instances: List<ProviderRow> = emptyList()
        var attendees: List<ProviderRow> = emptyList()
        var reminders: List<ProviderRow> = emptyList()
        var insertedIds: List<Long?> = listOf(1L)
        var failure: Exception? = null
        val queries = mutableListOf<ProviderQuery>()
        val applied = mutableListOf<List<ProviderOp>>()
        val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        override val changes: Flow<Unit> get() = changed

        override fun query(query: ProviderQuery): List<ProviderRow> {
            failure?.let { throw it }
            queries += query
            val owner = query.args.firstOrNull()?.toLongOrNull()
            return when (query.table) {
                ProviderTable.CALENDARS -> calendars

                ProviderTable.INSTANCES -> instances

                ProviderTable.ATTENDEES -> attendees.filter { it[Attendees.EVENT_ID] == owner }

                ProviderTable.REMINDERS -> reminders.filter { it[Reminders.EVENT_ID] == owner }

                ProviderTable.EVENTS ->
                    if (query.selection.orEmpty().contains(Events.ORIGINAL_ID)) {
                        exceptions
                    } else {
                        events.filter { it[Events._ID] == owner }
                    }
            }
        }

        override fun apply(ops: List<ProviderOp>): List<Long?> {
            failure?.let { throw it }
            applied.add(ops)
            return ops.indices.map { insertedIds.getOrNull(it) }
        }
    }

    private companion object {
        const val OWNED = 1L
        const val READ_ONLY = 2L
        const val NO_OWNER = 3L
    }
}
