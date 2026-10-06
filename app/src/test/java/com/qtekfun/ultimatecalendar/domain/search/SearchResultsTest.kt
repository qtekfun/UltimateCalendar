// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchResultsTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-10-06T12:00:00Z")

    private fun time(start: String) = Instant.parse(start).let {
        EventTime.Timed(it, it.plusSeconds(3_600), ZoneOffset.UTC)
    }

    private fun event(
        id: Long,
        title: String,
        start: String,
        repeats: Boolean = false,
        location: String? = null
    ) = SearchableEvent(
        EventId(id),
        CalendarId(1),
        title,
        time(start),
        location = location,
        isRecurring = repeats
    )

    private fun occurrence(id: Long, start: String) =
        EventInstance(EventId(id), CalendarId(1), "occurrence", time(start), isRecurring = true)

    private fun build(
        query: String,
        events: List<SearchableEvent>,
        occurrences: List<EventInstance> = emptyList()
    ): SearchSections {
        val words = SearchQuery.of(query)
        return SearchResults.build(
            events.mapNotNull { SearchMatcher.match(words, it) },
            occurrences,
            now,
            madrid
        )
    }

    private fun SearchSections.ids(section: List<SearchResult>) =
        section.map { it.match.event.eventId.value }

    @Test
    fun `results are split into what is coming and what is over`() {
        val sections = build(
            "x",
            listOf(
                event(1, "x past", "2026-09-01T09:00:00Z"),
                event(2, "x soon", "2026-10-07T09:00:00Z")
            )
        )

        assertEquals(listOf(2L), sections.ids(sections.upcoming))
        assertEquals(listOf(1L), sections.ids(sections.past))
        assertEquals(2, sections.size)
    }

    @Test
    fun `a series is listed once, at its next occurrence`() {
        val series = event(1, "x gym", "2026-01-05T07:00:00Z", repeats = true)
        val occurrences = listOf(
            occurrence(1, "2026-09-28T07:00:00Z"),
            occurrence(1, "2026-10-12T07:00:00Z"),
            occurrence(1, "2026-10-05T07:00:00Z"),
            occurrence(2, "2026-10-08T07:00:00Z")
        )

        val sections = build("gym", listOf(series), occurrences)

        val shown = sections.upcoming.single()
        assertEquals(Instant.parse("2026-10-12T07:00:00Z"), shown.instance.time.startIn(madrid))
        assertTrue(sections.past.isEmpty())
    }

    @Test
    fun `a series with no occurrence in the read window is shown at its own first time`() {
        val series = event(1, "x gym", "2020-01-05T07:00:00Z", repeats = true)

        val sections = build("gym", listOf(series))

        assertEquals(series.asInstance(), sections.past.single().instance)
    }

    @Test
    fun `the best match comes first in a section, then the nearest to now`() {
        val sections = build(
            "plan",
            listOf(
                event(1, "Other", "2026-10-07T09:00:00Z", location = "plan room"),
                event(2, "Plan B", "2026-10-20T09:00:00Z"),
                event(3, "Plan A", "2026-10-09T09:00:00Z"),
                event(4, "Old plan", "2026-08-01T09:00:00Z"),
                event(5, "Older plan", "2026-06-01T09:00:00Z"),
                event(6, "Plan zero", "2026-09-01T09:00:00Z")
            )
        )

        // Titles that start with the word beat the one that merely has it; ties go by date.
        assertEquals(listOf(3L, 2L, 1L), sections.ids(sections.upcoming))
        assertEquals(listOf(6L, 4L, 5L), sections.ids(sections.past))
    }

    @Test
    fun `an empty search gives empty sections`() {
        val sections = build("nothing", listOf(event(1, "Lunch", "2026-10-07T09:00:00Z")))

        assertTrue(sections.isEmpty)
        assertEquals(0, sections.size)
    }

    @Test
    fun `no more than the limit are listed, the coming ones first`() {
        val coming = (1..SearchResults.MAX_RESULTS - 10).map {
            event(it.toLong(), "x$it", "2026-10-10T09:00:00Z")
        }
        val over = (1..40).map { event(1_000L + it, "x$it", "2026-09-01T09:00:00Z") }

        val sections = build("x", coming + over)

        assertEquals(SearchResults.MAX_RESULTS, sections.size)
        assertEquals(coming.size, sections.upcoming.size)
        assertEquals(10, sections.past.size)
    }

    @Test
    fun `coming results alone can fill the limit`() {
        val coming = (1..SearchResults.MAX_RESULTS + 5).map {
            event(it.toLong(), "x$it", "2026-10-10T09:00:00Z")
        }

        val sections = build("x", coming)

        assertEquals(SearchResults.MAX_RESULTS, sections.upcoming.size)
        assertTrue(sections.past.isEmpty())
    }
}
