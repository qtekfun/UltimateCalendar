// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import com.qtekfun.ultimatecalendar.data.source.ProviderCalendarSource
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.Reminder
import com.qtekfun.ultimatecalendar.domain.model.ReminderMethod
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The bulk read of occurrences with their reminders: two queries whatever the number of events,
 * joined by event id. How the real provider answers is the contract suite's job.
 */
class ProviderReminderReadsTest {
    private val noon = Instant.parse("2026-10-06T10:00:00Z")
    private val range = TimeRange(noon.minusSeconds(3_600), noon.plusSeconds(10 * DAY))
    private val gateway = Gateway()
    private val source = ProviderCalendarSource(gateway, Dispatchers.Unconfined)

    private fun instance(
        event: Long,
        begin: Instant = noon,
        hasAlarm: Boolean = true,
        vararg extra: Pair<String, Any?>
    ): ProviderRow = mapOf(
        Instances.EVENT_ID to event,
        Instances.CALENDAR_ID to 1L,
        Instances.BEGIN to begin.toEpochMilli(),
        Instances.END to begin.plusSeconds(3_600).toEpochMilli(),
        Instances.TITLE to "T$event",
        Instances.ALL_DAY to 0L,
        Instances.EVENT_TIMEZONE to "Europe/Madrid",
        Instances.HAS_ALARM to if (hasAlarm) 1L else 0L
    ) + extra

    private fun reminder(event: Long, minutes: Int, method: Int = Reminders.METHOD_ALERT) = mapOf(
        Reminders.EVENT_ID to event,
        Reminders.MINUTES to minutes.toLong(),
        Reminders.METHOD to method.toLong()
    )

    private suspend fun read(calendars: Set<CalendarId>? = null) =
        (source.instancesWithReminders(range, calendars) as CalendarResult.Success).value

    @Test
    fun `an occurrence comes with the reminders of its event, their methods and its notes`() =
        runTest {
            gateway.instances = listOf(
                instance(7, extra = arrayOf(Instances.DESCRIPTION to "https://meet.example.com/a"))
            )
            gateway.reminders = listOf(
                reminder(7, 10),
                reminder(7, 60, Reminders.METHOD_EMAIL),
                reminder(8, 5)
            )

            val found = read().single()

            assertEquals(EventId(7), found.instance.eventId)
            assertEquals(
                listOf(Reminder(10), Reminder(60, ReminderMethod.EMAIL)),
                found.reminders
            )
            assertEquals("https://meet.example.com/a", found.description)
            assertFalse(found.usesDefaults)
        }

    @Test
    fun `an event that asks for the calendar defaults is marked and keeps its own reminders`() =
        runTest {
            gateway.instances = listOf(instance(1), instance(2))
            gateway.reminders = listOf(
                reminder(1, Reminders.MINUTES_DEFAULT),
                reminder(2, 30),
                reminder(2, Reminders.MINUTES_DEFAULT)
            )

            val found = read().associateBy { it.instance.eventId.value }

            assertTrue(found.getValue(1).usesDefaults)
            assertEquals(emptyList<Reminder>(), found.getValue(1).reminders)
            assertTrue(found.getValue(2).usesDefaults)
            assertEquals(listOf(Reminder(30)), found.getValue(2).reminders)
        }

    @Test
    fun `events without an alarm are not looked up, and empty notes are none`() = runTest {
        gateway.instances = listOf(
            instance(1, hasAlarm = false, extra = arrayOf(Instances.DESCRIPTION to "")),
            instance(2)
        )
        gateway.reminders = listOf(reminder(1, 10), reminder(2, 20))

        val found = read().associateBy { it.instance.eventId.value }

        assertEquals(emptyList<Reminder>(), found.getValue(1).reminders)
        assertNull(found.getValue(1).description)
        assertEquals(listOf(Reminder(20)), found.getValue(2).reminders)
        assertEquals(listOf("2"), gateway.reminderQueries.single().args)
    }

    @Test
    fun `a changed occurrence is reported under its series and has the reminders of its own`() =
        runTest {
            gateway.instances = listOf(
                instance(5, noon),
                instance(9, noon.plusSeconds(DAY), extra = arrayOf(Instances.ORIGINAL_ID to 5L))
            )
            gateway.reminders = listOf(reminder(5, 15), reminder(9, 5))

            val found = read()

            assertEquals(listOf(5L, 5L), found.map { it.instance.eventId.value })
            assertEquals(
                listOf(listOf(Reminder(15)), listOf(Reminder(5))),
                found.map { it.reminders }
            )
        }

    @Test
    fun `cancelled occurrences, other calendars and what is out of range are left out`() = runTest {
        gateway.instances = listOf(
            instance(1, extra = arrayOf(Events.STATUS to Events.STATUS_CANCELED.toLong())),
            instance(2, extra = arrayOf(Instances.CALENDAR_ID to 2L)),
            instance(3, begin = range.end.plusSeconds(60)),
            instance(4, begin = noon.plusSeconds(HOUR))
        )
        gateway.reminders = (1L..4L).map { reminder(it, 10) }

        assertEquals(listOf(4L), read(setOf(CalendarId(1))).map { it.instance.eventId.value })
        assertEquals(
            listOf(2L, 4L),
            read(null).map { it.instance.eventId.value }.sorted()
        )
        assertEquals(emptyList<Any>(), read(emptySet()))
    }

    @Test
    fun `a reminder the provider repeats is read once`() = runTest {
        gateway.instances = listOf(instance(1))
        gateway.reminders = listOf(reminder(1, 15), reminder(1, 15), reminder(1, 5))

        assertEquals(listOf(Reminder(15), Reminder(5)), read().single().reminders)
    }

    @Test
    fun `the occurrences come sorted by start`() = runTest {
        gateway.instances = listOf(
            instance(1, noon.plusSeconds(2 * HOUR)),
            instance(2, noon),
            instance(3, noon.plusSeconds(HOUR))
        )

        assertEquals(listOf(2L, 3L, 1L), read().map { it.instance.eventId.value })
    }

    @Test
    fun `many events are read in two queries, in batches that stay under the SQLite limit`() =
        runTest {
            val count = ProviderReminderReads.BATCH * 2 + 1
            gateway.instances = (1L..count).map { instance(it, noon.plusSeconds(it)) }
            gateway.reminders = (1L..count).map { reminder(it, 10) }

            val found = read()

            assertEquals(count, found.size)
            assertTrue(found.all { it.reminders == listOf(Reminder(10)) })
            assertEquals(1, gateway.instanceQueries)
            assertEquals(3, gateway.reminderQueries.size)
            assertTrue(gateway.reminderQueries.all { it.args.size <= ProviderReminderReads.BATCH })
            assertTrue(
                gateway.reminderQueries.all {
                    it.selection == "${Reminders.EVENT_ID} IN (${
                        it.args.joinToString(",") { "?" }
                    })"
                }
            )
        }

    @Test
    fun `no event with an alarm means no reminders query`() = runTest {
        gateway.instances = listOf(instance(1, hasAlarm = false))

        assertEquals(1, read().size)
        assertEquals(0, gateway.reminderQueries.size)
    }

    @Test
    fun `a calendar default row is recognised by its negative minutes only`() {
        assertTrue(ReminderMapping.isDefault(mapOf(Reminders.MINUTES to -1L)))
        assertFalse(ReminderMapping.isDefault(mapOf(Reminders.MINUTES to 0L)))
        assertFalse(ReminderMapping.isDefault(emptyMap()))
    }

    private class Gateway : ProviderGateway {
        var instances: List<ProviderRow> = emptyList()
        var reminders: List<ProviderRow> = emptyList()
        var instanceQueries = 0
        val reminderQueries = mutableListOf<ProviderQuery>()
        override val changes: Flow<Unit> = emptyFlow()

        override fun query(query: ProviderQuery): List<ProviderRow> = when (query.table) {
            ProviderTable.INSTANCES -> {
                instanceQueries++
                instances
            }

            ProviderTable.REMINDERS -> {
                reminderQueries += query
                val ids = query.args.map { it.toLong() }.toSet()
                reminders.filter { it[Reminders.EVENT_ID] in ids }
            }

            else -> error("unexpected query on ${query.table}")
        }

        override fun apply(ops: List<ProviderOp>): List<Long?> = error("read only")
    }

    private companion object {
        const val HOUR = 3_600L
        const val DAY = 86_400L
    }
}
