// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import com.qtekfun.ultimatecalendar.domain.subscriptions.SubscriptionError
import com.qtekfun.ultimatecalendar.notify.SystemZone
import java.time.DateTimeException
import java.time.Duration
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.junit5.StartStop
import okhttp3.Headers.Companion.headersOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionRefresherTest {
    @StartStop
    val server = MockWebServer()

    private val rig by lazy { SubscriptionRig(server) }

    @AfterEach
    fun close() = rig.close()

    private fun feed(vararg events: String) = FeedIcs.calendar(*events)

    private fun ok(body: String, etag: String? = "\"v1\"") =
        MockResponse(200, if (etag == null) headersOf() else headersOf("ETag", etag), body)

    private suspend fun titles(id: Long) = rig.events.inSubscription(id).map { it.title }.sorted()

    @Test
    fun `the first refresh stores the events and what it learned`() = runTest {
        val id = rig.addRow()
        server.enqueue(
            ok(
                feed(
                    FeedIcs.event("a@x", "First"),
                    FeedIcs.event("b@x", "Second"),
                    "BEGIN:VEVENT\nUID:bad@x\nEND:VEVENT\n"
                )
            )
        )

        val result = rig.refresher.refresh(id)

        assertEquals(RefreshResult.Updated(2, 1), result)
        assertEquals(listOf("First", "Second"), titles(id))
        val row = rig.subscriptions.get(id)!!
        assertEquals("\"v1\"", row.etag)
        assertEquals(2, row.eventCount)
        assertEquals(1, row.skipped)
        assertEquals(rig.clock.millis(), row.lastAttemptAt)
        assertEquals(rig.clock.millis(), row.lastSuccessAt)
        assertNull(row.error)
    }

    @Test
    fun `an unchanged feed costs a conditional request and keeps everything`() = runTest {
        val id = rig.addRow()
        server.enqueue(ok(feed(FeedIcs.event("a@x", "First"))))
        rig.refresher.refresh(id)
        server.takeRequest()
        rig.clock.advance(Duration.ofHours(12))
        server.enqueue(MockResponse(304))

        val result = rig.refresher.refresh(id)

        assertEquals(RefreshResult.Unchanged, result)
        assertEquals("\"v1\"", server.takeRequest().headers["If-None-Match"])
        assertEquals(listOf("First"), titles(id))
        val row = rig.subscriptions.get(id)!!
        assertEquals(rig.clock.millis(), row.lastSuccessAt)
        assertEquals("\"v1\"", row.etag)
        assertEquals(1, row.eventCount)
    }

    @Test
    fun `events keep their ids when the feed changes and gone ones are deleted`() = runTest {
        val id = rig.addRow()
        server.enqueue(ok(feed(FeedIcs.event("a@x", "Old title"), FeedIcs.event("b@x", "Gone"))))
        rig.refresher.refresh(id)
        val before = rig.events.inSubscription(id).associateBy { it.uid }
        server.enqueue(
            ok(feed(FeedIcs.event("a@x", "New title"), FeedIcs.event("c@x", "Added")), "\"v2\"")
        )

        rig.refresher.refresh(id)

        val after = rig.events.inSubscription(id).associateBy { it.uid }
        assertEquals(setOf("a@x", "c@x"), after.keys)
        assertEquals(before.getValue("a@x").id, after.getValue("a@x").id)
        assertEquals("New title", after.getValue("a@x").title)
        assertNull(rig.events.get(before.getValue("b@x").id))
        assertEquals("\"v2\"", rig.subscriptions.get(id)!!.etag)
    }

    @Test
    fun `a failure keeps the events and the validators and says what happened`() = runTest {
        val id = rig.addRow()
        server.enqueue(ok(feed(FeedIcs.event("a@x", "Kept"))))
        rig.refresher.refresh(id)
        val success = rig.clock.millis()
        rig.clock.advance(Duration.ofHours(12))
        server.enqueue(MockResponse(503))

        val result = rig.refresher.refresh(id)

        assertEquals(RefreshResult.Failed(SubscriptionError.HTTP, 503), result)
        assertEquals(listOf("Kept"), titles(id))
        val row = rig.subscriptions.get(id)!!
        assertEquals("HTTP", row.error)
        assertEquals(503, row.errorCode)
        assertEquals(rig.clock.millis(), row.lastAttemptAt)
        assertEquals(success, row.lastSuccessAt)
        assertEquals("\"v1\"", row.etag)
        assertEquals(1, row.eventCount)
    }

    @Test
    fun `a later success clears the error`() = runTest {
        val id = rig.addRow()
        server.enqueue(MockResponse(500))
        rig.refresher.refresh(id)
        server.enqueue(ok(feed(FeedIcs.event("a@x", "Back"))))

        rig.refresher.refresh(id)

        val row = rig.subscriptions.get(id)!!
        assertNull(row.error)
        assertNull(row.errorCode)
        assertEquals(listOf("Back"), titles(id))
    }

    @Test
    fun `an answer that is not a calendar is an error and changes nothing`() = runTest {
        val id = rig.addRow()
        server.enqueue(ok(feed(FeedIcs.event("a@x", "Kept"))))
        rig.refresher.refresh(id)
        server.enqueue(ok("<html>Log in please</html>", etag = null))

        val result = rig.refresher.refresh(id)

        assertEquals(RefreshResult.Failed(SubscriptionError.NOT_CALENDAR, null), result)
        assertEquals(listOf("Kept"), titles(id))
        assertEquals("\"v1\"", rig.subscriptions.get(id)!!.etag)
    }

    @Test
    fun `a zone that cannot be read counts as a feed that cannot be read`() = runTest {
        server.enqueue(ok(feed(FeedIcs.event("a@x", "A"))))
        server.enqueue(ok(feed(FeedIcs.event("a@x", "A"))))
        listOf(
            SystemZone { throw DateTimeException("no zone") },
            SystemZone { throw IllegalArgumentException("no zone") }
        ).forEach { broken ->
            val bad = SubscriptionRig(server, zoneOf = broken)
            try {
                val id = bad.addRow()

                assertEquals(
                    RefreshResult.Failed(SubscriptionError.NOT_CALENDAR, null),
                    bad.refresher.refresh(id)
                )
            } finally {
                bad.close()
            }
        }
    }

    @Test
    fun `an address that cannot be decrypted is an error of its own`() = runTest {
        val id = rig.addRow()
        rig.subscriptions.update(rig.subscriptions.get(id)!!.copy(urlSecret = "garbage"))

        assertEquals(
            RefreshResult.Failed(SubscriptionError.UNREADABLE_URL, null),
            rig.refresher.refresh(id)
        )
        assertEquals(0, server.requestCount)
        assertEquals("UNREADABLE_URL", rig.subscriptions.get(id)!!.error)
    }

    @Test
    fun `a subscription that does not exist is gone`() = runTest {
        assertEquals(RefreshResult.Gone, rig.refresher.refresh(99))
    }

    @Test
    fun `a subscription removed during its download is gone, whatever came back`() = runTest {
        val id = rig.addRow()
        val removeFirst = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                runBlocking { rig.subscriptions.delete(id) }
                return ok(feed(FeedIcs.event("a@x", "A")))
            }
        }
        server.dispatcher = removeFirst

        assertEquals(RefreshResult.Gone, rig.refresher.refresh(id))

        val again = rig.addRow(path = "/other.ics")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                runBlocking { rig.subscriptions.delete(again) }
                return MockResponse(500)
            }
        }
        assertEquals(RefreshResult.Gone, rig.refresher.refresh(again))
    }

    @Test
    fun `what is being downloaded shows while it runs`() = runTest {
        val id = rig.addRow()
        val seen = mutableListOf<Set<Long>>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += rig.refresher.refreshing.value
                return ok(feed())
            }
        }

        rig.refresher.refresh(id)

        assertEquals(listOf(setOf(id)), seen)
        assertEquals(emptySet<Long>(), rig.refresher.refreshing.value)
    }

    @Test
    fun `refreshing all skips what is switched off`() = runTest {
        val on = rig.addRow("/on.ics", "On")
        val off = rig.addRow("/off.ics", "Off", enabled = false)
        server.enqueue(ok(feed(FeedIcs.event("a@x", "A"))))

        val summary = rig.refresher.refreshAll()

        assertEquals(RefreshSummary(1, 0), summary)
        assertEquals(1, server.requestCount)
        assertEquals(1, rig.subscriptions.get(on)!!.eventCount)
        assertNull(rig.subscriptions.get(off)!!.lastAttemptAt)
    }

    @Test
    fun `a periodic run refreshes what is due and counts the failures worth retrying`() = runTest {
        val due = rig.addRow("/due.ics", "Due")
        val manual = rig.addRow("/manual.ics", "Manual", hours = 0)
        val recent = rig.addRow("/recent.ics", "Recent")
        rig.subscriptions.update(
            rig.subscriptions.get(recent)!!.copy(lastAttemptAt = rig.clock.millis())
        )
        server.enqueue(MockResponse(503))

        val summary = rig.refresher.refreshDue(retrying = false)

        assertEquals(RefreshSummary(1, 1), summary)
        assertEquals(1, server.requestCount)
        assertNotNull(rig.subscriptions.get(due)!!.error)
        assertNull(rig.subscriptions.get(manual)!!.lastAttemptAt)
    }

    @Test
    fun `a retry takes the ones that failed, even if they are not due again`() = runTest {
        val id = rig.addRow()
        server.enqueue(MockResponse(500))
        rig.refresher.refreshDue(retrying = false)
        server.enqueue(ok(feed(FeedIcs.event("a@x", "Now"))))

        val quiet = rig.refresher.refreshDue(retrying = false)
        val retry = rig.refresher.refreshDue(retrying = true)

        assertEquals(RefreshSummary(0, 0), quiet)
        assertEquals(RefreshSummary(1, 0), retry)
        assertTrue(rig.subscriptions.get(id)!!.error == null)
    }

    @Test
    fun `permanent failures are not retried soon`() = runTest {
        rig.addRow()
        server.enqueue(MockResponse(404))

        assertEquals(RefreshSummary(1, 0), rig.refresher.refreshDue(retrying = false))
    }
}
