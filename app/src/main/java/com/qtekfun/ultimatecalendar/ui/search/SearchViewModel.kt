// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.search.SearchOutcome
import com.qtekfun.ultimatecalendar.data.search.SearchRepository
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.search.RecentSearches
import com.qtekfun.ultimatecalendar.domain.search.SearchQuery
import com.qtekfun.ultimatecalendar.domain.search.SearchSections
import com.qtekfun.ultimatecalendar.notify.SystemZone
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the search is. */
enum class SearchStatus {
    /** Nothing typed: the screen offers the recent searches. */
    IDLE,

    /** Waiting for the typing to pause, or reading. */
    SEARCHING,

    DONE,

    /** The calendars could not be read (permission, provider). */
    FAILED
}

/** What the search screen draws. */
data class SearchState(
    val query: String = "",
    val includeHidden: Boolean = false,
    val status: SearchStatus = SearchStatus.IDLE,
    val sections: SearchSections = SearchSections(),
    val calendarColors: Map<CalendarId, Int> = emptyMap(),
    val recent: List<String> = emptyList(),
    val zone: ZoneId = ZoneId.systemDefault()
)

/**
 * Feeds the search screen (RF-09). The text goes through a short debounce, so a search runs when
 * the typing pauses and an older one is dropped when a newer starts; the IME's search action, the
 * hidden-calendars switch and a recent search run at once. What the user searches is remembered
 * only on an explicit search or when a result is opened, and is never logged.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val recent: RecentSearches,
    private val zone: SystemZone
) : ViewModel() {
    private data class Request(
        val query: String,
        val includeHidden: Boolean,
        val immediate: Boolean
    )

    private data class Found(val request: Request, val failed: Boolean, val outcome: SearchOutcome?)

    private val request = MutableStateFlow(Request("", includeHidden = false, immediate = true))

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val found: Flow<Found?> = request
        .debounce { if (it.immediate || it.query.isBlank()) 0L else DEBOUNCE_MS }
        .mapLatest { current ->
            if (SearchQuery.of(current.query).isBlank) {
                null
            } else {
                when (val result = repository.search(current.query, current.includeHidden)) {
                    is CalendarResult.Failure -> Found(current, failed = true, outcome = null)
                    is CalendarResult.Success -> Found(current, failed = false, result.value)
                }
            }
        }
        .onStart { emit(null) }

    val state: StateFlow<SearchState> = combine(request, found, recent.searches) {
            asked,
            last,
            saved
        ->
        val status = statusOf(asked, last)
        SearchState(
            query = asked.query,
            includeHidden = asked.includeHidden,
            status = status,
            sections = last?.outcome?.sections.takeIf { status != SearchStatus.IDLE }
                ?: SearchSections(),
            calendarColors = last?.outcome?.calendarColors.orEmpty(),
            recent = saved,
            zone = zone.current()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchState())

    /** What is being searched now: a screen that comes back shows it again. */
    val currentQuery: String get() = request.value.query

    /** The text changed: searches when the typing pauses. */
    fun onQuery(text: String) = request.update { it.copy(query = text, immediate = false) }

    /** The IME's search action, or a recent search chosen: searches now and remembers it. */
    fun submit(text: String = request.value.query) {
        request.update { it.copy(query = text, immediate = true) }
        remember(text)
    }

    fun setIncludeHidden(include: Boolean) =
        request.update { it.copy(includeHidden = include, immediate = true) }

    /** A result was opened: the search that found it is worth keeping. */
    fun onResultOpened() = remember(request.value.query)

    fun forgetRecent(query: String) {
        viewModelScope.launch { recent.forget(query) }
    }

    fun clearRecent() {
        viewModelScope.launch { recent.clear() }
    }

    private fun remember(text: String) {
        if (!SearchQuery.of(text).isBlank) viewModelScope.launch { recent.remember(text) }
    }

    private fun statusOf(asked: Request, last: Found?): SearchStatus = when {
        SearchQuery.of(asked.query).isBlank -> SearchStatus.IDLE

        // The shown result answers an older request: the new one is still being read.
        last == null || last.request.query != asked.query ||
            last.request.includeHidden != asked.includeHidden -> SearchStatus.SEARCHING

        last.failed -> SearchStatus.FAILED

        else -> SearchStatus.DONE
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
