// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.settings

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.subscriptions.AddResult
import com.qtekfun.ultimatecalendar.data.subscriptions.FeedIcs
import com.qtekfun.ultimatecalendar.data.subscriptions.RefreshResult
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionRig
import com.qtekfun.ultimatecalendar.domain.subscriptions.RefreshInterval
import com.qtekfun.ultimatecalendar.domain.subscriptions.SubscriptionError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.Headers.Companion.headersOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsViewModelTest {
    @StartStop
    val server = MockWebServer()

    private val rig by lazy { SubscriptionRig(server) }
    private val viewModel by lazy { SubscriptionsViewModel(rig.repository, rig.refresher) }

    @BeforeEach
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        rig.close()
    }

    private fun feed(title: String = "A"): MockResponse {
        val body = FeedIcs.calendar(FeedIcs.event("a@x", title))
        return MockResponse(200, headersOf("ETag", "\"v1\""), body)
    }

    @Test
    fun `the list shows each subscription and whether it is being downloaded`() = runTest {
        viewModel.rows.test {
            assertEquals(emptyList<SubscriptionRow>(), awaitItem())
            val id = rig.addRow(name = "Holidays")

            val row = awaitItem().single()
            assertEquals("Holidays", row.subscription.name)
            assertEquals(id, row.subscription.id)
            assertFalse(row.refreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a refresh the user asked for says how it went`() = runTest {
        val id = rig.addRow()
        server.enqueue(feed())
        server.enqueue(MockResponse(304))
        server.enqueue(MockResponse(503))

        viewModel.notes.test {
            viewModel.refresh(id).join()
            assertEquals(SubscriptionNote.Refreshed(1, 0), awaitItem())
            viewModel.refresh(id).join()
            assertEquals(SubscriptionNote.Unchanged, awaitItem())
            viewModel.refresh(id).join()
            assertEquals(
                SubscriptionNote.Failed(RefreshResult.Failed(SubscriptionError.HTTP, 503)),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshing one that was removed says nothing`() = runTest {
        viewModel.notes.test {
            viewModel.refresh(404).join()

            expectNoEvents()
        }
    }

    @Test
    fun `switching one on refreshes it at once, switching it off does not`() = runTest {
        val id = rig.addRow(enabled = false)
        server.enqueue(feed())

        viewModel.notes.test {
            viewModel.setEnabled(id, false).join()
            expectNoEvents()
            viewModel.setEnabled(id, true).join()
            assertEquals(SubscriptionNote.Refreshed(1, 0), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(rig.repository.all().single().enabled)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `editing, the interval and removal reach the repository`() = runTest {
        val id = rig.addRow(name = "Old")

        viewModel.edit(id, "  New  ", 0xFF123456.toInt()).join()
        viewModel.setInterval(id, RefreshInterval.MANUAL).join()

        val subscription = rig.repository.all().single()
        assertEquals("New", subscription.name)
        assertEquals(0xFF123456.toInt(), subscription.color)
        assertEquals(RefreshInterval.MANUAL, subscription.interval)

        viewModel.remove(id).join()

        assertEquals(emptyList<Any>(), rig.repository.all())
    }

    @Test
    fun `refresh all downloads what is switched on`() = runTest {
        rig.addRow("/on.ics")
        rig.addRow("/off.ics", enabled = false)
        server.enqueue(feed())

        viewModel.refreshAll().join()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an address that is refused says why and nothing is downloaded`() = runTest {
        assertEquals(
            AddResult.Insecure,
            viewModel.add("http://cal.example.com/a.ics", "", 1, RefreshInterval.DEFAULT)
        )
        assertEquals(
            AddResult.Invalid,
            viewModel.add("not a url", "", 1, RefreshInterval.DEFAULT)
        )
        assertEquals(0, server.requestCount)
        assertEquals(emptyList<Any>(), rig.repository.all())
    }

    @Test
    fun `an address that is accepted is added and downloaded straight away`() = runTest {
        viewModel.notes.test {
            // Nothing listens here, so the download fails at once and the note says so.
            val result = viewModel.add(
                "https://127.0.0.1:1/a.ics",
                "Mine",
                1,
                RefreshInterval.DEFAULT
            )

            assertTrue(result is AddResult.Added)
            assertEquals(
                SubscriptionNote.Failed(RefreshResult.Failed(SubscriptionError.UNREACHABLE)),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("Mine", rig.repository.all().single().name)
        assertEquals(
            AddResult.Duplicate,
            viewModel.add("https://127.0.0.1:1/a.ics", "Again", 1, RefreshInterval.DEFAULT)
        )
    }
}
