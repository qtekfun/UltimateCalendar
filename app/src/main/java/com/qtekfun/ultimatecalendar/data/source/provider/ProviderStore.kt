// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.result.CalendarError

/**
 * What the provider holds, looked up the way the source needs it. Lookups that find nothing end
 * the operation with [abort] (`NotFound`, `ReadOnly`), so callers read as a straight line.
 */
internal class ProviderStore(private val gateway: ProviderGateway) {
    fun calendars(): List<CalendarInfo> =
        gateway.query(ProviderQuery(ProviderTable.CALENDARS, CalendarMapping.projection))
            .mapNotNull(CalendarMapping::toCalendar)

    fun calendar(id: CalendarId): CalendarInfo =
        calendars().firstOrNull { it.id == id } ?: abort(CalendarError.NotFound)

    /** The stored row of an event that still exists (the provider keeps deleted ones for sync). */
    fun eventRow(id: EventId): ProviderRow = gateway.query(
        ProviderQuery(
            table = ProviderTable.EVENTS,
            projection = EventMapping.projection,
            selection = "${Events._ID}=?",
            args = listOf(id.value.toString())
        )
    ).firstOrNull { !it.flag(Events.DELETED) } ?: abort(CalendarError.NotFound)

    /** The calendar of the event [row], when the user may change its events. */
    fun editableCalendar(row: ProviderRow): CalendarInfo {
        val calendar = calendar(requireNotNull(EventMapping.calendarOf(row)))
        if (!calendar.access.canEdit) abort(CalendarError.ReadOnly)
        return calendar
    }

    fun event(row: ProviderRow): Event {
        val id = requireNotNull(row.long(Events._ID))
        val attendees = children(ProviderTable.ATTENDEES, AttendeeMapping.projection, id)
            .mapNotNull(AttendeeMapping::toAttendee)
        val reminders = children(ProviderTable.REMINDERS, ReminderMapping.projection, id)
            .mapNotNull(ReminderMapping::toReminder)
        return EventMapping.toEvent(row, attendees, reminders) ?: abort(CalendarError.NotFound)
    }

    /** The rows of [table] (attendees or reminders) that belong to the event [eventId]. */
    fun children(table: ProviderTable, projection: List<String>, eventId: Long): List<ProviderRow> =
        gateway.query(
            ProviderQuery(table, projection, "${Attendees.EVENT_ID}=?", listOf(eventId.toString()))
        )

    /** The id of the exception event that changes the occurrence at [originalMs], if any. */
    fun exceptionOf(id: EventId, originalMs: Long): Long? = gateway.query(
        ProviderQuery(
            table = ProviderTable.EVENTS,
            projection = listOf(Events._ID),
            selection = "${Events.ORIGINAL_ID}=? AND ${Events.ORIGINAL_INSTANCE_TIME}=? " +
                "AND ${Events.DELETED}=0",
            args = listOf(id.value.toString(), originalMs.toString())
        )
    ).firstOrNull()?.long(Events._ID)

    /** Whether the series has an occurrence that starts exactly at [originalMs]. */
    fun hasInstance(id: EventId, originalMs: Long): Boolean = gateway.query(
        ProviderQuery(
            table = ProviderTable.INSTANCES,
            projection = InstanceMapping.projection,
            rangeMs = originalMs..originalMs
        )
    ).any {
        it.long(Instances.EVENT_ID) == id.value && InstanceMapping.beginOf(it) == originalMs
    }

    /** Applies [ops] in one batch and returns the ids of the rows it inserted. */
    fun insert(ops: List<ProviderOp>): List<Long?> = gateway.apply(ops)

    /** Applies [ops] in one batch. */
    fun write(ops: List<ProviderOp>) {
        gateway.apply(ops)
    }
}
