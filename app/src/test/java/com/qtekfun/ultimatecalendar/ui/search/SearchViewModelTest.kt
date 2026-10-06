// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.search

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.local.dao.CalendarSettingsDao
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DefaultCalendarEntity
import com.qtekfun.ultimatecalendar.data.search.SearchRepository
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.data.source.UnavailableCalendarSource
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.CalendarSettings
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.domain.search.RecentSearchList
import com.qtekfun.ultimatecalendar.domain.search.RecentSearches
import com.qtekfun.ultimatecalendar.domain.search.SearchableEvent
import com.qtekfun.ultimatecalendar.notify.SystemZone
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val clock = Clock.fixed(Instant.parse("2026-10-06T09:30:00Z"), ZoneOffset.UTC)
    private val account = CalendarAccount("me@example.com", "com.google")
    private val work = calendar(1, "Work", 0xFF112233.toInt())
    private val home = calendar(2, "Home", 0xFF445566.toInt())

    private lateinit var source: FakeCalendarSource
    private lateinit var calendars: CalendarRepository
    private lateinit var searches: CountingSource
    private val recent = FakeRecentSearches()

    private fun calendar(id: Long, name: String, color: Int) = CalendarInfo(
        CalendarId(id),
        account,
        name,
        color,
        CalendarAccess.OWNER,
        ownerEmail = "me@example.com"
    )

    @BeforeEach
    fun open() {
        Dispatchers.setMain(StandardTestDispatcher())
        source = FakeCalendarSource(listOf(work, home))
        searches = CountingSource(source)
        calendars = CalendarRepository(source, MemoryDao(), Dispatchers.Unconfined)
    }

    @AfterEach
    fun close() {
        Dispatchers.resetMain()
    }

    private fun viewModel(on: CalendarSource = searches) = SearchViewModel(
        SearchRepository(on, calendars, clock, SystemZone { madrid }, Dispatchers.Unconfined),
        recent,
        SystemZone { madrid }
    )

    private suspend fun add(calendar: CalendarInfo, title: String, start: String) {
        source.create(
            EventDraft(
                calendarId = calendar.id,
                title = title,
                time = Instant.parse(start).let {
                    EventTime.Timed(it, it.plusSeconds(3_600), madrid)
                }
            )
        )
    }

    /** Keeps the state flow subscribed, as the screen does, so it can be read at any moment. */
    private fun TestScope.watch(model: SearchViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
    }

    private fun SearchViewModel.titles() = state.value.sections.let { sections ->
        (sections.upcoming + sections.past).map { it.match.event.title }
    }

    @Test
    fun `at first nothing is searched and the recent searches are offered`() = runTest {
        recent.searches.value = listOf("budget", "lunch")

        viewModel().state.test {
            var shown = awaitItem()
            while (shown.recent.isEmpty()) shown = awaitItem()

            assertEquals(SearchStatus.IDLE, shown.status)
            assertEquals(listOf("budget", "lunch"), shown.recent)
            assertEquals(madrid, shown.zone)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, searches.calls)
    }

    @Test
    fun `the state goes from searching to done with the results`() = runTest {
        add(work, "Budget review", "2026-10-08T09:00:00Z")
        val model = viewModel()

        model.state.test {
            assertEquals(SearchStatus.IDLE, awaitItem().status)
            model.onQuery("budget")

            var shown = awaitItem()
            assertEquals(SearchStatus.SEARCHING, shown.status)
            while (shown.status == SearchStatus.SEARCHING) shown = awaitItem()

            assertEquals(SearchStatus.DONE, shown.status)
            assertEquals(1, shown.sections.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a search waits for the typing to pause`() = runTest {
        add(work, "Budget review", "2026-10-08T09:00:00Z")
        val model = viewModel()
        watch(model)
        runCurrent()

        model.onQuery("budget")
        runCurrent()
        assertEquals(SearchStatus.SEARCHING, model.state.value.status)
        assertEquals("budget", model.state.value.query)

        advanceTimeBy(299)
        assertEquals(SearchStatus.SEARCHING, model.state.value.status)
        assertEquals(0, searches.calls)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(SearchStatus.DONE, model.state.value.status)
        assertEquals(listOf("Budget review"), model.titles())
        assertEquals(1, searches.calls)
    }

    @Test
    fun `typing on only searches for the last text`() = runTest {
        add(work, "Budget review", "2026-10-08T09:00:00Z")
        val model = viewModel()
        watch(model)
        runCurrent()

        "budget".indices.forEach {
            model.onQuery("budget".take(it + 1))
            advanceTimeBy(100)
        }
        advanceUntilIdle()

        assertEquals(1, searches.calls)
        assertEquals(listOf("budget"), searches.queries)
        assertEquals(listOf("Budget review"), model.titles())
    }

    @Test
    fun `while the next search runs the previous results stay in view`() = runTest {
        add(work, "Budget review", "2026-10-08T09:00:00Z")
        add(work, "Lunch", "2026-10-08T12:00:00Z")
        val model = viewModel()
        watch(model)
        model.onQuery("budget")
        advanceUntilIdle()

        model.onQuery("lunch")
        runCurrent()

        assertEquals(SearchStatus.SEARCHING, model.state.value.status)
        assertEquals(listOf("Budget review"), model.titles())

        advanceUntilIdle()
        assertEquals(listOf("Lunch"), model.titles())
    }

    @Test
    fun `clearing the text goes back to idle at once, with no search`() = runTest {
        add(work, "Budget review", "2026-10-08T09:00:00Z")
        val model = viewModel()
        watch(model)
        model.onQuery("budget")
        advanceUntilIdle()

        model.onQuery("")
        runCurrent()

        assertEquals(SearchStatus.IDLE, model.state.value.status)
        assertTrue(model.state.value.sections.isEmpty)
        assertEquals(1, searches.calls)
        model.onQuery("   ")
        advanceUntilIdle()
        assertEquals(1, searches.calls)
    }

    @Test
    fun `the search action searches at once and remembers the search`() = runTest {
        add(work, "Budget review", "2026-10-08T09:00:00Z")
        val model = viewModel()
        watch(model)
        runCurrent()

        model.submit("Budget")
        runCurrent()

        assertEquals(SearchStatus.DONE, model.state.value.status)
        assertEquals(listOf("Budget review"), model.titles())
        assertEquals(listOf("Budget"), recent.searches.value)
        assertEquals(listOf("Budget"), model.state.value.recent)
    }

    @Test
    fun `typing alone remembers nothing, opening a result does`() = runTest {
        add(work, "Budget review", "2026-10-08T09:00:00Z")
        val model = viewModel()
        watch(model)
        model.onQuery("budget")
        advanceUntilIdle()
        assertEquals(emptyList<String>(), recent.searches.value)

        model.onResultOpened()
        runCurrent()

        assertEquals(listOf("budget"), recent.searches.value)
        model.onQuery("")
        model.onResultOpened()
        runCurrent()
        assertEquals(listOf("budget"), recent.searches.value)
    }

    @Test
    fun `a recent search can be run again, forgotten or all cleared`() = runTest {
        add(work, "Budget review", "2026-10-08T09:00:00Z")
        recent.searches.value = listOf("lunch", "budget")
        val model = viewModel()
        watch(model)
        runCurrent()

        model.submit("budget")
        runCurrent()
        assertEquals(listOf("Budget review"), model.titles())
        assertEquals(listOf("budget", "lunch"), model.state.value.recent)

        model.forgetRecent("lunch")
        runCurrent()
        assertEquals(listOf("budget"), model.state.value.recent)

        model.clearRecent()
        runCurrent()
        assertEquals(emptyList<String>(), model.state.value.recent)
    }

    @Test
    fun `hidden calendars are searched when the switch is on, at once`() = runTest {
        add(work, "Budget work", "2026-10-08T09:00:00Z")
        add(home, "Budget home", "2026-10-09T09:00:00Z")
        calendars.saveSettings(home.id, CalendarSettings(visible = false))
        val model = viewModel()
        watch(model)
        model.onQuery("budget")
        advanceUntilIdle()
        assertEquals(listOf("Budget work"), model.titles())

        model.setIncludeHidden(true)
        runCurrent()

        assertTrue(model.state.value.includeHidden)
        assertEquals(listOf("Budget work", "Budget home"), model.titles())
        assertEquals(
            mapOf(work.id to work.color, home.id to home.color),
            model.state.value.calendarColors
        )
    }

    @Test
    fun `a search with no match is done and empty`() = runTest {
        add(work, "Budget review", "2026-10-08T09:00:00Z")
        val model = viewModel()
        watch(model)

        model.onQuery("zzz")
        advanceUntilIdle()

        assertEquals(SearchStatus.DONE, model.state.value.status)
        assertTrue(model.state.value.sections.isEmpty)
    }

    @Test
    fun `a source that fails makes the search fail`() = runTest {
        val model = viewModel(UnavailableCalendarSource)
        watch(model)

        model.onQuery("budget")
        advanceUntilIdle()

        assertEquals(SearchStatus.FAILED, model.state.value.status)
        assertTrue(model.state.value.sections.isEmpty)
    }

    @Test
    fun `a screen that comes back can read what is being searched`() = runTest {
        val model = viewModel()
        assertEquals("", model.currentQuery)

        model.onQuery("budget")

        assertEquals("budget", model.currentQuery)
    }

    /** Counts what is asked of the source, to see when a search really runs. */
    private class CountingSource(private val inner: CalendarSource) : CalendarSource by inner {
        var calls = 0
        val queries = mutableListOf<String>()

        override suspend fun search(
            query: String,
            calendarIds: Set<CalendarId>?,
            range: TimeRange?
        ): CalendarResult<List<SearchableEvent>> {
            calls++
            queries += query
            return inner.search(query, calendarIds, range)
        }
    }

    /** The calendar settings in memory, so nothing here waits for a real thread. */
    private class MemoryDao : CalendarSettingsDao {
        private val rows = MutableStateFlow(emptyMap<Long, CalendarSettingsEntity>())
        private val default = MutableStateFlow<Long?>(null)

        override fun observeAll(): Flow<List<CalendarSettingsEntity>> = rows.map {
            it.values.toList()
        }

        override suspend fun all() = rows.value.values.toList()

        override suspend fun find(calendarId: Long) = rows.value[calendarId]

        override suspend fun save(settings: CalendarSettingsEntity) {
            rows.value += settings.calendarId to settings
        }

        override suspend fun clear(calendarId: Long) {
            rows.value -= calendarId
        }

        override fun observeDefaultCalendar(): Flow<Long?> = default

        override suspend fun saveDefaultCalendar(default: DefaultCalendarEntity) {
            this.default.value = default.calendarId
        }

        override suspend fun clearDefaultCalendar() {
            default.value = null
        }
    }

    private class FakeRecentSearches : RecentSearches {
        override val searches = MutableStateFlow(emptyList<String>())

        override suspend fun remember(query: String) {
            searches.value = RecentSearchList.add(searches.value, query)
        }

        override suspend fun forget(query: String) {
            searches.value = RecentSearchList.remove(searches.value, query)
        }

        override suspend fun clear() {
            searches.value = emptyList()
        }
    }
}
