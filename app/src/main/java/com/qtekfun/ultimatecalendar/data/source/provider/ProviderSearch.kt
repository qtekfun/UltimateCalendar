// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.source.provider

import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.search.SearchQuery
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import com.qtekfun.ultimatecalendar.domain.search.SqlLike

/**
 * Looks for events in the provider with parameterized `LIKE` queries: the text the user typed is
 * only ever a bound argument (escaped by [SqlLike]), never part of the SQL. `LIKE` is just a
 * filter that cannot leave out a real match; the caller decides with `SearchMatcher`. Events come
 * from two queries (their own text, and the attendees' names and addresses), then their
 * attendees are read in batches so a long list of ids never reaches SQLite's limit of variables.
 */
internal class ProviderSearch(private val gateway: ProviderGateway) {
    /** Events (not deleted, cancelled or single changed occurrences) that may match [query]. */
    fun candidates(query: SearchQuery, calendarIds: Set<CalendarId>?): List<SearchableEvent> {
        val pattern = SqlLike.contains(SqlLike.mostSelective(query.words))
        val scope = scope(calendarIds)
        val byText = events(
            scope.where + " AND (${like(Events.TITLE)} OR ${like(Events.EVENT_LOCATION)} OR " +
                "${like(Events.DESCRIPTION)})",
            scope.args + List(TEXT_FIELDS) { pattern }
        )
        val byAttendee = eventsOfAttendees(pattern)
            .minus(byText.mapNotNull { it.long(Events._ID) }.toSet())
            .chunked(BATCH)
            .flatMap { ids ->
                events(
                    scope.where + " AND ${Events._ID} IN (${marks(ids.size)})",
                    scope.args + ids.map(Long::toString)
                )
            }
        val rows = byText + byAttendee
        val attendees = attendeesOf(rows.mapNotNull { it.long(Events._ID) })
        return rows.mapNotNull { row ->
            EventMapping.toEvent(row, attendees[row.long(Events._ID)].orEmpty(), emptyList())
        }.map { SearchableEvent.of(it) }
    }

    /** The events that have an occurrence in [range], as `Instances` reports them. */
    fun eventsInRange(range: TimeRange): Set<EventId> = gateway.query(
        ProviderQuery(
            table = ProviderTable.INSTANCES,
            projection = InstanceMapping.projection,
            rangeMs = range.start.toEpochMilli()..range.end.toEpochMilli()
        )
    ).mapNotNull { InstanceMapping.toInstance(it, range)?.eventId }.toSet()

    private class Scope(val where: String, val args: List<String>)

    private fun scope(calendarIds: Set<CalendarId>?): Scope {
        val base = "${Events.DELETED}=0 AND ${Events.ORIGINAL_ID} IS NULL AND " +
            "(${Events.STATUS} IS NULL OR ${Events.STATUS}!=${Events.STATUS_CANCELED})"
        return if (calendarIds == null) {
            Scope(base, emptyList())
        } else {
            Scope(
                "$base AND ${Events.CALENDAR_ID} IN (${marks(calendarIds.size)})",
                calendarIds.map { it.value.toString() }
            )
        }
    }

    private fun events(selection: String, args: List<String>): List<ProviderRow> = gateway.query(
        ProviderQuery(ProviderTable.EVENTS, EventMapping.projection, selection, args)
    )

    private fun eventsOfAttendees(pattern: String): Set<Long> = gateway.query(
        ProviderQuery(
            table = ProviderTable.ATTENDEES,
            projection = listOf(Attendees.EVENT_ID),
            selection = "${like(Attendees.ATTENDEE_NAME)} OR ${like(Attendees.ATTENDEE_EMAIL)}",
            args = listOf(pattern, pattern)
        )
    ).mapNotNull { it.long(Attendees.EVENT_ID) }.toSet()

    private fun attendeesOf(eventIds: List<Long>): Map<Long, List<Attendee>> = eventIds
        .chunked(BATCH)
        .flatMap { ids ->
            gateway.query(
                ProviderQuery(
                    table = ProviderTable.ATTENDEES,
                    projection = AttendeeMapping.projection + Attendees.EVENT_ID,
                    selection = "${Attendees.EVENT_ID} IN (${marks(ids.size)})",
                    args = ids.map(Long::toString)
                )
            )
        }
        .groupBy { it.long(Attendees.EVENT_ID) }
        .mapNotNull { (id, rows) -> id?.let { it to rows.mapNotNull(AttendeeMapping::toAttendee) } }
        .toMap()

    private fun like(column: String) = "$column LIKE ? ESCAPE '${SqlLike.ESCAPE}'"

    private fun marks(count: Int) = List(count) { "?" }.joinToString(",")

    private companion object {
        const val TEXT_FIELDS = 3
        const val BATCH = 400
    }
}
