// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.search

import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.search.SearchMatcher
import com.qtekfun.ultimatecalendar.domain.search.SearchQuery
import com.qtekfun.ultimatecalendar.domain.search.SearchResults
import com.qtekfun.ultimatecalendar.domain.search.SearchSections
import com.qtekfun.ultimatecalendar.notify.SystemZone
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** What a search found, and the color of each calendar for events that have none of their own. */
data class SearchOutcome(val sections: SearchSections, val calendarColors: Map<CalendarId, Int>)

/**
 * Search over the calendars the user can see (RF-09): asks the [CalendarSource] for the events
 * that match, then for the occurrences of the repeating ones around today, and hands both to the
 * domain to choose, rank and group. Hidden calendars are left out unless [search] is asked to
 * include them.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val source: CalendarSource,
    private val calendars: CalendarRepository,
    private val clock: Clock,
    private val zone: SystemZone,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun search(
        query: String,
        includeHidden: Boolean,
        range: TimeRange? = null
    ): CalendarResult<SearchOutcome> = withContext(io) {
        val words = SearchQuery.of(query)
        when (val all = calendars.calendars().first()) {
            is CalendarResult.Failure -> all

            is CalendarResult.Success -> {
                val scope = all.value.filter { includeHidden || it.visible }
                val ids = scope.map { it.id }.toSet()
                val colors = scope.associate { it.id to it.color }
                if (words.isBlank || ids.isEmpty()) {
                    CalendarResult.Success(SearchOutcome(SearchSections(), colors))
                } else {
                    sectionsFor(query, words, ids, range).map { SearchOutcome(it, colors) }
                }
            }
        }
    }

    private suspend fun sectionsFor(
        query: String,
        words: SearchQuery,
        ids: Set<CalendarId>,
        range: TimeRange?
    ): CalendarResult<SearchSections> = when (val found = source.search(query, ids, range)) {
        is CalendarResult.Failure -> found

        is CalendarResult.Success -> {
            val matches = found.value.mapNotNull { SearchMatcher.match(words, it) }
            val now = clock.instant()
            // Only a repeating event has occurrences to choose from; one read serves them all.
            val window = range
                ?: TimeRange(now.minus(OCCURRENCE_WINDOW), now.plus(OCCURRENCE_WINDOW))
            val repeating = matches.filter { it.event.isRecurring }.map { it.event.eventId }.toSet()
            // If the occurrences cannot be read the events are still listed, at their first one.
            val occurrences = if (repeating.isEmpty()) {
                emptyList()
            } else {
                source.instances(window, ids).getOrNull().orEmpty()
                    .filter { it.eventId in repeating }
            }
            CalendarResult.Success(SearchResults.build(matches, occurrences, now, zone.current()))
        }
    }

    private companion object {
        /** How far around today the occurrences of a repeating event are looked for. */
        val OCCURRENCE_WINDOW: Duration = Duration.ofDays(366)
    }
}
