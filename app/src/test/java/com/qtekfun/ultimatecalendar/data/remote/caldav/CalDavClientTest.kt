// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote.caldav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.junit5.StartStop
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalDavClientTest {
    @StartStop
    val server = MockWebServer()

    /** Nextcloud installed under a path, as many self-hosted servers are. */
    private val client by lazy {
        CalDavClient(OkHttpClient(), server.url("/nextcloud/"), Dispatchers.Unconfined)
    }
    private val writes by lazy {
        CalDavWrites(OkHttpClient(), server.url("/nextcloud/"), Dispatchers.Unconfined)
    }

    private fun multistatus(vararg responses: String, syncToken: String? = null) = MockResponse(
        207,
        headersOf("Content-Type", "application/xml; charset=utf-8"),
        """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:" """ +
            """xmlns:cal="urn:ietf:params:xml:ns:caldav" """ +
            """xmlns:cs="http://calendarserver.org/ns/" xmlns:x1="http://apple.com/ns/ical/">""" +
            responses.joinToString("") +
            (syncToken?.let { "<d:sync-token>$it</d:sync-token>" } ?: "") +
            "</d:multistatus>"
    )

    private fun response(href: String, props: String, status: String = "HTTP/1.1 200 OK") =
        "<d:response><d:href>$href</d:href><d:propstat><d:prop>$props</d:prop>" +
            "<d:status>$status</d:status></d:propstat></d:response>"

    private fun components(name: String) =
        "<cal:supported-calendar-component-set><cal:comp name=\"$name\"/></cal:supported-calendar-component-set>"

    private fun privileges(vararg names: String) = "<d:current-user-privilege-set>" +
        names.joinToString("") { "<d:privilege><d:$it/></d:privilege>" } +
        "</d:current-user-privilege-set>"

    private fun RecordedRequest.text() = body?.utf8().orEmpty()

    @Test
    fun `discovery follows the principal to the calendar home`() = runTest {
        server.enqueue(
            multistatus(
                response(
                    "/nextcloud/remote.php/dav/",
                    "<d:current-user-principal>" +
                        "<d:href>/nextcloud/remote.php/dav/principals/users/ana/</d:href>" +
                        "</d:current-user-principal>"
                )
            )
        )
        server.enqueue(
            multistatus(
                response(
                    "/nextcloud/remote.php/dav/principals/users/ana/",
                    "<cal:calendar-home-set>" +
                        "<d:href>/nextcloud/remote.php/dav/calendars/ana/</d:href>" +
                        "</cal:calendar-home-set>"
                )
            )
        )
        assertEquals(
            DavResult.Success("/nextcloud/remote.php/dav/calendars/ana/"),
            client.discover()
        )
        val first = server.takeRequest()
        assertEquals("PROPFIND /nextcloud/remote.php/dav/", "${first.method} ${first.target}")
        assertEquals("0", first.headers["Depth"])
        assertTrue("current-user-principal" in first.text())
        assertEquals("/nextcloud/remote.php/dav/principals/users/ana/", server.takeRequest().target)
    }

    private fun principalAnswers(userProperties: String) {
        server.enqueue(
            multistatus(
                response(
                    "/nextcloud/remote.php/dav/",
                    "<d:current-user-principal><d:href>/nextcloud/principals/ana/</d:href>" +
                        "</d:current-user-principal>"
                )
            )
        )
        server.enqueue(
            multistatus(
                response(
                    "/nextcloud/principals/ana/",
                    "<cal:calendar-home-set><d:href>/nextcloud/calendars/ana/</d:href>" +
                        "</cal:calendar-home-set>$userProperties"
                )
            )
        )
    }

    @Test
    fun `the profile adds the user's addresses and the scheduling outbox to the home`() = runTest {
        principalAnswers(
            "<cal:calendar-user-address-set>" +
                "<d:href>mailto:Ana@Example.com</d:href><d:href>/principals/ana/</d:href>" +
                "<d:href>MAILTO:ana@work.example</d:href><d:href>mailto:ana@example.com</d:href>" +
                "</cal:calendar-user-address-set>" +
                "<cal:schedule-outbox-URL><d:href>/nextcloud/calendars/ana/outbox/</d:href>" +
                "</cal:schedule-outbox-URL>"
        )

        val profile = (client.profile() as DavResult.Success).value

        assertEquals("/nextcloud/calendars/ana/", profile.home)
        assertEquals(listOf("ana@example.com", "ana@work.example"), profile.addresses)
        assertEquals("/nextcloud/calendars/ana/outbox/", profile.schedulingOutbox)
        assertTrue(profile.schedules)
        server.takeRequest()
        val asked = server.takeRequest().text()
        assertTrue("calendar-user-address-set" in asked && "schedule-outbox-URL" in asked)
    }

    @Test
    fun `a server without scheduling properties gives a profile that does not schedule`() =
        runTest {
            principalAnswers("")

            val profile = (client.profile() as DavResult.Success).value

            assertEquals(emptyList<String>(), profile.addresses)
            assertNull(profile.schedulingOutbox)
            assertFalse(profile.schedules)
        }

    @Test
    fun `an outbox without an address is not enough to schedule`() = runTest {
        principalAnswers(
            "<cal:schedule-outbox-URL><d:href>/outbox/</d:href></cal:schedule-outbox-URL>"
        )

        assertFalse((client.profile() as DavResult.Success).value.schedules)
    }

    @Test
    fun `a profile needs a principal and a home`() = runTest {
        server.enqueue(
            multistatus(response("/nextcloud/remote.php/dav/", "<d:displayname>x</d:displayname>"))
        )
        assertEquals(DavResult.ParseError, client.profile())

        server.takeRequest()
        server.enqueue(
            multistatus(
                response(
                    "/nextcloud/remote.php/dav/",
                    "<d:current-user-principal><d:href>/p/</d:href></d:current-user-principal>"
                )
            )
        )
        server.enqueue(multistatus(response("/p/", "<d:displayname>no home</d:displayname>")))
        assertEquals(DavResult.ParseError, client.profile())
    }

    @Test
    fun `discovery without a principal is a parse error`() = runTest {
        server.enqueue(
            multistatus(response("/nextcloud/remote.php/dav/", "<d:displayname>x</d:displayname>"))
        )
        assertEquals(DavResult.ParseError, client.discover())
    }

    @Test
    fun `calendars keep event calendars and read their properties`() = runTest {
        val calendar = "<d:resourcetype><d:collection/><cal:calendar/></d:resourcetype>"
        val events = components("VEVENT")
        val tasksOnly = components("VTODO")
        val write = privileges("read", "write")
        val read = privileges("read")
        server.enqueue(
            multistatus(
                response("/cal/ana/", "<d:resourcetype><d:collection/></d:resourcetype>"),
                response(
                    "/cal/ana/personal/",
                    "$calendar$events$write<d:displayname>Personal &amp; casa</d:displayname>" +
                        "<x1:calendar-color>#ff9500ff</x1:calendar-color>" +
                        "<x1:calendar-order>2</x1:calendar-order>" +
                        "<d:sync-token>http://sabre.io/ns/sync/7</d:sync-token><cs:getctag>ctag-7</cs:getctag>"
                ),
                response(
                    "/cal/ana/tasks/",
                    "$calendar$tasksOnly<d:displayname>Tasks</d:displayname>"
                ),
                response(
                    "/cal/ana/shared_by_bob/",
                    "$calendar$read<d:displayname>Bob</d:displayname>"
                ) +
                    "<d:response><d:href>/cal/ana/shared_by_bob/</d:href><d:propstat>" +
                    "<d:prop><x1:calendar-color/>" +
                    "</d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response>",
                response("/cal/ana/plain/", "$calendar<x1:calendar-color>red</x1:calendar-color>"),
                response("/cal/ana/trashbin/", "<d:resourcetype><d:collection/></d:resourcetype>")
            )
        )
        val result = client.calendars("/cal/ana/") as DavResult.Success
        assertEquals(
            listOf(
                DavCollection(
                    "/cal/ana/personal/",
                    "Personal & casa",
                    "#FF9500",
                    2,
                    true,
                    "http://sabre.io/ns/sync/7",
                    "ctag-7"
                ),
                DavCollection("/cal/ana/shared_by_bob/", "Bob", null, null, false, null, null),
                DavCollection("/cal/ana/plain/", "plain", null, null, true, null, null)
            ),
            result.value.distinctBy { it.href }
        )
        val request = server.takeRequest()
        assertEquals("1", request.headers["Depth"])
        assertTrue("calendar-order" in request.text())
    }

    @Test
    fun `changes split changed and deleted events and bring the new token`() = runTest {
        server.enqueue(
            multistatus(
                response("/cal/l/a.ics", "<d:getetag>\"1\"</d:getetag>"),
                "<d:response><d:href>/cal/l/gone.ics</d:href><d:status>HTTP/1.1 404 Not Found</d:status></d:response>",
                syncToken = "http://sabre.io/ns/sync/9"
            )
        )
        assertEquals(
            DavResult.Success(
                DavChanges(
                    listOf(DavResource("/cal/l/a.ics", "\"1\"")),
                    listOf("/cal/l/gone.ics"),
                    "http://sabre.io/ns/sync/9"
                )
            ),
            client.changes("/cal/l/", "http://sabre.io/ns/sync/8")
        )
        val request = server.takeRequest()
        assertEquals("REPORT", request.method)
        assertTrue("<d:sync-token>http://sabre.io/ns/sync/8</d:sync-token>" in request.text())
    }

    @Test
    fun `a first sync sends an empty token, an expired one asks for a full pull`() = runTest {
        server.enqueue(multistatus())
        assertEquals(
            DavResult.Success(DavChanges(emptyList(), emptyList(), null)),
            client.changes("/cal/l/", null)
        )
        assertTrue("<d:sync-token></d:sync-token>" in server.takeRequest().text())
        server.enqueue(
            MockResponse(
                403,
                headersOf(),
                "<d:error xmlns:d=\"DAV:\"><d:valid-sync-token/></d:error>"
            )
        )
        assertEquals(DavResult.SyncTokenExpired, client.changes("/cal/l/", "old"))
        server.enqueue(
            MockResponse(
                409,
                headersOf(),
                "<d:error xmlns:d=\"DAV:\"><d:valid-sync-token/></d:error>"
            )
        )
        assertEquals(DavResult.SyncTokenExpired, client.changes("/cal/l/", "old"))
    }

    @Test
    fun `fetch brings the iCalendar text of each event`() = runTest {
        server.enqueue(
            multistatus(
                response(
                    "/cal/l/a.ics",
                    "<d:getetag>\"1\"</d:getetag>" +
                        "<cal:calendar-data>BEGIN:VCALENDAR&#13;\nEND:VCALENDAR&#13;\n</cal:calendar-data>"
                ),
                "<d:response><d:href>/cal/l/b.ics</d:href><d:status>HTTP/1.1 404 Not Found</d:status></d:response>"
            )
        )
        assertEquals(
            DavResult.Success(
                listOf(DavResource("/cal/l/a.ics", "\"1\"", "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"))
            ),
            client.fetch("/cal/l/", listOf("/cal/l/a.ics", "/cal/l/b&c.ics"))
        )
        val body = server.takeRequest().text()
        assertTrue("calendar-multiget" in body && "<d:href>/cal/l/b&amp;c.ics</d:href>" in body)
    }

    @Test
    fun `all events calendars hrefs and ETags`() = runTest {
        server.enqueue(multistatus(response("/cal/l/a.ics", "<d:getetag>\"1\"</d:getetag>")))
        assertEquals(
            DavResult.Success(listOf(DavResource("/cal/l/a.ics", "\"1\""))),
            client.allEvents("/cal/l/")
        )
        assertTrue("comp-filter name=\"VEVENT\"" in server.takeRequest().text())
    }

    @Test
    fun `new events must not exist, updates must match the ETag`() = runTest {
        server.enqueue(MockResponse(201, headersOf("ETag", "\"1\"")))
        assertEquals(
            DavResult.Success("\"1\""),
            writes.put("/cal/l/a.ics", "BEGIN:VCALENDAR", etag = null)
        )
        val create = server.takeRequest()
        assertEquals("*", create.headers["If-None-Match"])
        assertEquals("text/calendar; charset=utf-8", create.headers["Content-Type"])
        assertEquals("BEGIN:VCALENDAR", create.text())
        server.enqueue(MockResponse(204))
        assertEquals(DavResult.Success(null), writes.put("/cal/l/a.ics", "x", etag = "\"1\""))
        assertEquals("\"1\"", server.takeRequest().headers["If-Match"])
        server.enqueue(MockResponse(412))
        assertEquals(DavResult.PreconditionFailed, writes.put("/cal/l/a.ics", "x", etag = "\"0\""))
    }

    @Test
    fun `delete and move`() = runTest {
        server.enqueue(MockResponse(204))
        assertEquals(DavResult.Success(Unit), writes.delete("/cal/l/a.ics", "\"1\""))
        assertEquals("\"1\"", server.takeRequest().headers["If-Match"])
        server.enqueue(MockResponse(404))
        assertEquals(DavResult.NotFound, writes.delete("/cal/l/a.ics", null))
        assertNull(server.takeRequest().headers["If-Match"])
        server.enqueue(MockResponse(201))
        assertEquals(DavResult.Success(Unit), writes.move("/cal/l/a.ics", "/cal/m/a.ics", "\"1\""))
        val move = server.takeRequest()
        assertEquals("MOVE", move.method)
        assertEquals(server.url("/cal/m/a.ics").toString(), move.headers["Destination"])
        assertEquals("F", move.headers["Overwrite"])
        assertEquals("\"1\"", move.headers["If-Match"])
        server.enqueue(MockResponse(201))
        writes.move("/cal/l/a.ics", "/cal/m/a.ics", null)
        assertNull(server.takeRequest().headers["If-Match"])
    }

    @Test
    fun `calendars are created, changed and deleted`() = runTest {
        server.enqueue(MockResponse(201))
        val created = writes.createCalendar(
            "/cal/ana",
            "Compra <casa>",
            "#FF9500"
        ) as DavResult.Success
        assertTrue(created.value.matches(Regex("/cal/ana/[0-9a-f-]{36}/")))
        val mkcalendar = server.takeRequest()
        assertEquals("MKCALENDAR ${created.value}", "${mkcalendar.method} ${mkcalendar.target}")
        assertTrue(
            "Compra &lt;casa&gt;" in mkcalendar.text() && "name=\"VEVENT\"" in mkcalendar.text()
        )
        server.enqueue(multistatus())
        assertEquals(
            DavResult.Success(Unit),
            writes.updateCalendar("/cal/ana/x/", null, "#34C759", 3)
        )
        val patch = server.takeRequest().text()
        assertTrue(
            "<a:calendar-color>#34C759</a:calendar-color><a:calendar-order>3</a:calendar-order>" in
                patch
        )
        assertTrue("displayname" !in patch)
        server.enqueue(MockResponse(204))
        assertEquals(DavResult.Success(Unit), writes.deleteCalendar("/cal/ana/x/"))
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `server answers become results`() = runTest {
        listOf(
            401 to DavResult.Unauthorized,
            403 to DavResult.Forbidden,
            500 to DavResult.HttpError(500)
        ).forEach { (code, expected) ->
            server.enqueue(MockResponse(code))
            assertEquals(expected, writes.deleteCalendar("/cal/x/"))
        }
        server.enqueue(MockResponse(207, headersOf(), "<html>not xml"))
        assertEquals(DavResult.ParseError, client.calendars("/cal/"))
        server.enqueue(MockResponse(207, headersOf(), "<other xmlns=\"DAV:\"/>"))
        assertEquals(DavResult.ParseError, client.calendars("/cal/"))
        server.enqueue(
            MockResponse(
                207,
                headersOf(),
                "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>" +
                    "<d:multistatus xmlns:d=\"DAV:\">&e;</d:multistatus>"
            )
        )
        assertEquals(DavResult.ParseError, client.calendars("/cal/"))
    }
}
