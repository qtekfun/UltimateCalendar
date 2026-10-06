// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.reminders.EventReminders
import java.time.ZoneOffset

/**
 * The occurrences of a range with their reminders (RF-08), in two provider queries whatever the
 * number of events: one on `Instances` (which expands the repetitions) and one on `Reminders`
 * for the events that have an alarm, joined here by event id. The `IN` lists are cut in batches
 * so that they stay under SQLite's limit of variables.
 */
internal class ProviderReminderReads(private val gateway: ProviderGateway) {
    fun read(range: TimeRange, calendarIds: Set<CalendarId>?): List<EventReminders> {
        val rows = gateway.query(
            ProviderQuery(
                table = ProviderTable.INSTANCES,
                projection = PROJECTION,
                rangeMs = range.start.toEpochMilli()..range.end.toEpochMilli()
            )
        ).mapNotNull { row ->
            InstanceMapping.toInstance(row, range)?.let { row to it }
        }.filter { (_, instance) -> calendarIds == null || instance.calendarId in calendarIds }
        val ids = rows.filter { (row, _) -> row.flag(Instances.HAS_ALARM) }
            .mapNotNull { (row, _) -> row.long(Instances.EVENT_ID) }
            .distinct()
        val stored = remindersOf(ids)
        return rows.map { (row, instance) ->
            val own = stored[row.long(Instances.EVENT_ID)].orEmpty()
            EventReminders(
                instance = instance,
                reminders = own.mapNotNull(ReminderMapping::toReminder).distinct(),
                description = row.text(Instances.DESCRIPTION)?.ifEmpty { null },
                usesDefaults = own.any(ReminderMapping::isDefault)
            )
        }.sortedBy { it.instance.time.startIn(ZoneOffset.UTC) }
    }

    /** The `Reminders` rows of [eventIds], by event id. */
    private fun remindersOf(eventIds: List<Long>): Map<Long?, List<ProviderRow>> =
        eventIds.chunked(BATCH).flatMap { batch ->
            gateway.query(
                ProviderQuery(
                    table = ProviderTable.REMINDERS,
                    projection = listOf(Reminders.EVENT_ID, Reminders.MINUTES, Reminders.METHOD),
                    selection = "${Reminders.EVENT_ID} IN (${batch.joinToString(",") { "?" }})",
                    args = batch.map { it.toString() }
                )
            )
        }.groupBy { it.long(Reminders.EVENT_ID) }

    companion object {
        /** SQLite allows 999 variables in older versions; stay well under it. */
        const val BATCH = 500

        /** What a reminder needs of an occurrence: the usual columns, the notes and the alarm flag. */
        val PROJECTION: List<String> =
            InstanceMapping.projection + listOf(Instances.DESCRIPTION, Instances.HAS_ALARM)
    }
}
