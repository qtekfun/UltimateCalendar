// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import java.time.Instant
import java.time.ZoneId

/** One line of the results: an event that matched, the occurrence it stands for, and when. */
data class SearchResult(val match: EventMatch, val instance: EventInstance, val upcoming: Boolean)

/** The results split by time: what is still to come, and what is over. */
data class SearchSections(
    val upcoming: List<SearchResult> = emptyList(),
    val past: List<SearchResult> = emptyList()
) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && past.isEmpty()

    val size: Int get() = upcoming.size + past.size
}

/** Turns matches into the list the screen shows: one occurrence per event, ranked, in sections. */
object SearchResults {
    /** The most results shown; a search that matches more is too wide to be read anyway. */
    const val MAX_RESULTS = 300

    /**
     * Builds the sections from [matches] and the [occurrences] the source reports for them. In
     * each section the best match comes first and, among equals, the one nearest to [now]: soonest
     * for what is coming, most recent for what is over.
     */
    fun build(
        matches: List<EventMatch>,
        occurrences: List<EventInstance>,
        now: Instant,
        zone: ZoneId
    ): SearchSections {
        val byEvent = occurrences.groupBy { it.eventId }
        val results = matches.map { match ->
            val chosen = OccurrencePicker.pick(
                now,
                zone,
                byEvent[match.event.eventId].orEmpty(),
                match.event.asInstance()
            )
            SearchResult(match, chosen.instance, chosen.upcoming)
        }
        val (coming, over) = results.partition { it.upcoming }
        return SearchSections(
            upcoming = coming.sortedWith(
                compareByDescending<SearchResult> { it.match.score }
                    .thenBy { it.instance.time.startIn(zone) }
            ),
            past = over.sortedWith(
                compareByDescending<SearchResult> { it.match.score }
                    .thenByDescending { it.instance.time.startIn(zone) }
            )
        ).limited(MAX_RESULTS)
    }

    private fun SearchSections.limited(max: Int): SearchSections {
        val upcoming = upcoming.take(max)
        return SearchSections(upcoming, past.take(max - upcoming.size))
    }
}
