// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import com.qtekfun.ultimatecalendar.data.ical.IcsEvent
import com.qtekfun.ultimatecalendar.data.ical.IcsEvents
import com.qtekfun.ultimatecalendar.data.ical.IcsParser
import com.qtekfun.ultimatecalendar.data.local.StoredTimes
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.recurrence.EventSeries
import java.time.ZoneId

/**
 * What was read from a downloaded feed: the [events] (one per series, unique by UID) and how many
 * `VEVENT`s were left out, because they could not be read or because the feed has more than
 * [SubscriptionFeed.MAX_EVENTS] events.
 */
data class SubscriptionEvents(val events: List<IcsEvent>, val skipped: Int)

/**
 * Reads a subscription's iCalendar text with the same reader as CalDAV (`IcsEvents`), tolerating
 * what real feeds contain: `VTODO`, `VJOURNAL` and anything else but `VEVENT` is ignored, an
 * event that cannot be read is counted and skipped, and the rest is kept.
 *
 * A subscription never reminds and never invites: attendees and reminders are dropped here, so
 * nothing downstream can act on them.
 */
object SubscriptionFeed {
    const val MAX_EVENTS = 20_000

    /** The events of [text], or null when it is not a calendar at all. */
    fun parse(text: String, floating: ZoneId): SubscriptionEvents? {
        val calendars = IcsParser.parse(text).filter { it.name.equals("VCALENDAR", true) }
        if (calendars.isEmpty()) return null
        val placeholder = CalendarId(0) to EventId(0)
        val series = calendars.flatMap { calendar ->
            IcsEvents.readAll(calendar, placeholder.first, placeholder.second, floating)
        }
        val unique = withUniqueUids(series.map(::withoutGuests))
        val kept = unique.take(MAX_EVENTS)
        val total = calendars.sumOf { it.components("VEVENT").size }
        return SubscriptionEvents(kept, (total - kept.sumOf(::vevents)).coerceAtLeast(0))
    }

    /** The `VEVENT`s a series was made of: its master and its changed or cancelled occurrences. */
    private fun vevents(event: IcsEvent) = 1 + event.series.overrides.size

    private fun withoutGuests(event: IcsEvent): IcsEvent {
        val series = event.series
        return event.copy(
            series = EventSeries(
                event = series.event.withoutGuests(),
                exDates = series.exDates,
                rDates = series.rDates,
                overrides = series.overrides.map { override ->
                    override.copy(replacement = override.replacement?.withoutGuests())
                }
            )
        )
    }

    private fun Event.withoutGuests() = copy(attendees = emptyList(), reminders = emptyList())

    /**
     * Gives every event a UID of its own, which the store needs as the event's identity: an event
     * with no UID gets one made of its title and time (stable across downloads), and a UID that
     * appears again (two feeds merged by hand) is told apart by a counter.
     */
    private fun withUniqueUids(events: List<IcsEvent>): List<IcsEvent> {
        val seen = HashSet<String>()
        return events.map { event ->
            val base = event.uid.ifEmpty { fallbackUid(event) }
            var uid = base
            var counter = 1
            while (!seen.add(uid)) uid = "$base#${counter++}"
            if (uid == event.uid) event else event.copy(uid = uid)
        }
    }

    private fun fallbackUid(event: IcsEvent): String {
        val master = event.series.event
        val time = StoredTimes.columns(master.time)
        return "noid-" + (master.title + "|" + time.start + "|" + time.end).hashCode()
            .toUInt().toString(HEX)
    }

    private const val HEX = 16
}
