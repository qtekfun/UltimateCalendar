// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.search

import androidx.compose.runtime.Composable
import com.qtekfun.ultimatecalendar.domain.model.Attendee
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.search.SearchMatcher
import com.qtekfun.ultimatecalendar.domain.search.SearchQuery
import com.qtekfun.ultimatecalendar.domain.search.SearchResults
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import com.qtekfun.ultimatecalendar.ui.components.ComponentPreviews
import com.qtekfun.ultimatecalendar.ui.components.PreviewSurface
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// Invented data only.
private val madrid = ZoneId.of("Europe/Madrid")
private val now = Instant.parse("2026-10-06T09:30:00Z")
private const val BLUE = 0xFF3F51B5.toInt()
private const val GREEN = 0xFF0B8043.toInt()

private var lastId = 0L

private fun timed(title: String, start: String, color: Int, location: String? = null) =
    SearchableEvent(
        eventId = EventId(++lastId),
        calendarId = CalendarId(1),
        title = title,
        time = LocalDateTime.parse(start).atZone(madrid).toInstant().let {
            EventTime.Timed(it, it.plus(Duration.ofHours(1)), madrid)
        },
        location = location,
        color = color
    )

private val events = listOf(
    timed("Reunión de planificación", "2026-10-08T10:00", BLUE, "Sala 2"),
    timed("Weekly review", "2026-10-13T16:00", GREEN).copy(
        description = "Review the reunion notes and next steps for the sample project.",
        isRecurring = true
    ),
    timed("Coffee with Sam", "2026-09-21T09:00", BLUE).copy(
        attendees = listOf(Attendee.of("sam@example.com", "Sam Sample"))
    ),
    SearchableEvent(
        EventId(++lastId),
        CalendarId(1),
        "Team retreat",
        EventTime.AllDay(LocalDate.parse("2026-08-03"), LocalDate.parse("2026-08-05")),
        location = "Sample lodge near the reunion point",
        color = GREEN
    )
)

private fun state(query: String): SearchState {
    val words = SearchQuery.of(query)
    val matches = events.mapNotNull { SearchMatcher.match(words, it) }
    return SearchState(
        query = query,
        status = SearchStatus.DONE,
        sections = SearchResults.build(matches, emptyList(), now, madrid),
        zone = madrid
    )
}

@ComponentPreviews
@Composable
internal fun SearchResultsPreview() {
    PreviewSurface { SearchContent(state("reunion"), "reunion", SearchCallbacks()) }
}

@ComponentPreviews
@Composable
internal fun SearchRecentPreview() {
    PreviewSurface {
        SearchContent(
            SearchState(recent = listOf("reunion", "sam@example.com", "retreat")),
            "",
            SearchCallbacks()
        )
    }
}

@ComponentPreviews
@Composable
internal fun SearchEmptyPreview() {
    PreviewSurface { SearchContent(SearchState(), "", SearchCallbacks()) }
}

@ComponentPreviews
@Composable
internal fun SearchNoResultsPreview() {
    PreviewSurface {
        SearchContent(state("zzz").copy(query = "zzz"), "zzz", SearchCallbacks())
    }
}
