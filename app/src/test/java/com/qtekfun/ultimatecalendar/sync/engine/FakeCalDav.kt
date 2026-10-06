// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf

/**
 * A small CalDAV server in memory, enough for the sync: discovery, calendars, sync-collection
 * with tokens, multiget, and PUT/DELETE/MOVE honouring ETags. Paths are like Nextcloud's.
 */
class FakeCalDav : Dispatcher() {
    data class Resource(val etag: String, val ics: String)

    val calendars = linkedMapOf<String, String>()
    val resources = linkedMapOf<String, Resource>()
    private val log = mutableListOf<Pair<Int, String>>()
    private var version = 0
    private var nextEtag = 0
    val requests = mutableListOf<String>()

    /** Answers the next requests to [path] (any method) with [code], once per entry. */
    val failures = mutableMapOf<String, ArrayDeque<Int>>()

    /** Runs when a PUT arrives, before the server decides: the app is "still uploading". */
    var duringPut: (String) -> Unit = {}

    /** Decides to answer a request with a status code before anything else, or null to let it be. */
    var failOn: (method: String, path: String) -> Int? = { _, _ -> null }

    /** Calendars shared with the user without the right to write. */
    val readOnly = mutableSetOf<String>()

    val home = "/remote.php/dav/calendars/ana/"

    /** The addresses the principal answers with; empty for a server that does not tell. */
    var addresses: List<String> = listOf("me@example.com")

    /** Whether the principal has a scheduling outbox: the server sends invitations and answers. */
    var scheduling = true

    /**
     * What the server's scheduling would do on each PUT: `INVITE address` for each guest of an
     * event organized by the user and `REPLY STATUS` when the user's own answer changed.
     */
    val schedulingLog = mutableListOf<String>()

    /** Lists that answer sync-collection with 415, as Deck's do; they have a ctag instead. */
    val withoutSync = mutableSetOf<String>()

    fun addCalendar(name: String, slug: String = name.lowercase()): String =
        (home + slug + "/").also { calendars[it] = name }

    fun put(href: String, ics: String): String {
        val etag = "\"${++nextEtag}\""
        resources[href] = Resource(etag, ics)
        log += ++version to href
        return etag
    }

    fun remove(href: String) {
        resources.remove(href)
        log += ++version to href
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.target.substringBefore('?')
        requests += "${request.method} $path"
        val refused = failures[path]?.removeFirstOrNull() ?: failOn(request.method, path)
        refused?.let { return MockResponse(it) }
        val body = request.body?.utf8().orEmpty()
        return when {
            else -> dav(request.method, path, body, request)
        }
    }

    private fun dav(
        method: String,
        path: String,
        body: String,
        request: RecordedRequest
    ): MockResponse = when (method) {
        "PROPFIND" -> propfind(path)

        "REPORT" -> report(path, body)

        "PUT" -> write(
            path,
            body,
            request.headers["If-Match"],
            request.headers["If-None-Match"]
        )

        "DELETE" -> delete(path)

        "MKCALENDAR" -> createCalendar(path, body)

        "PROPPATCH" -> patchCalendar(path, body)

        "MOVE" -> move(path, request.headers["Destination"].orEmpty())

        else -> MockResponse(405)
    }

    private fun propfind(path: String): MockResponse = when (path) {
        "/remote.php/dav/" -> multistatus(
            response(
                path,
                "<d:current-user-principal><d:href>/remote.php/dav/principals/users/ana/</d:href>" +
                    "</d:current-user-principal>"
            )
        )

        "/remote.php/dav/principals/users/ana/" -> multistatus(
            response(
                path,
                "<c:calendar-home-set><d:href>$home</d:href></c:calendar-home-set>" +
                    "<c:calendar-user-address-set>" +
                    addresses.joinToString("") { "<d:href>mailto:$it</d:href>" } +
                    "<d:href>/remote.php/dav/principals/users/ana/</d:href>" +
                    "</c:calendar-user-address-set>" +
                    if (scheduling) {
                        "<c:schedule-outbox-URL><d:href>${path}outbox/</d:href></c:schedule-outbox-URL>"
                    } else {
                        ""
                    }
            )
        )

        home -> multistatus(
            *calendars.map { (href, name) ->
                response(
                    href,
                    "<d:displayname>$name</d:displayname>" +
                        "<d:resourcetype><d:collection/><c:calendar/></d:resourcetype>" +
                        "<c:supported-calendar-component-set>" +
                        "<c:comp name=\"VEVENT\"/></c:supported-calendar-component-set>" +
                        privileges(href) +
                        if (href in
                            withoutSync
                        ) {
                            "<cs:getctag>${ctag(href)}</cs:getctag>"
                        } else {
                            "<d:sync-token>$version</d:sync-token>"
                        }
                )
            }.toTypedArray()
        )

        else -> MockResponse(404)
    }

    private fun privileges(href: String) = if (href in readOnly) {
        "<d:current-user-privilege-set><d:privilege><d:read/></d:privilege></d:current-user-privilege-set>"
    } else {
        ""
    }

    private fun ctag(list: String) = log.lastOrNull { it.second.startsWith(list) }?.first ?: 0

    private fun report(path: String, body: String): MockResponse = when {
        "sync-collection" in body && path in withoutSync -> MockResponse(415)

        "calendar-query" in body -> multistatus(
            *resources.keys.filter {
                it.startsWith(path)
            }.map(::state).toTypedArray()
        )

        "sync-collection" in body -> {
            val token = Regex(
                "<d:sync-token>([^<]*)</d:sync-token>"
            ).find(body)?.groupValues?.get(1).orEmpty()
            val since = token.toIntOrNull()
            if (token.isNotEmpty() && since == null) {
                MockResponse(
                    403,
                    headersOf(),
                    "<d:error xmlns:d=\"DAV:\"><d:valid-sync-token/></d:error>"
                )
            } else {
                val hrefs = if (since == null) {
                    resources.keys.filter { it.startsWith(path) }
                } else {
                    log.filter {
                        it.first > since && it.second.startsWith(path)
                    }.map { it.second }.distinct()
                }
                multistatus(*hrefs.map(::state).toTypedArray(), token = "$version")
            }
        }

        "calendar-multiget" in body -> multistatus(
            *Regex("<d:href>([^<]*)</d:href>").findAll(body).map { it.groupValues[1] }.map { href ->
                resources[href]?.let {
                    response(
                        href,
                        "<d:getetag>${it.etag}</d:getetag><c:calendar-data>${escape(
                            it.ics
                        )}</c:calendar-data>"
                    )
                } ?: gone(href)
            }.toList().toTypedArray()
        )

        else -> MockResponse(400)
    }

    private fun write(
        path: String,
        body: String,
        ifMatch: String?,
        ifNoneMatch: String?
    ): MockResponse {
        duringPut(path)
        val current = resources[path]
        val refused =
            (ifNoneMatch == "*" && current != null) || (ifMatch != null && current?.etag != ifMatch)
        return if (refused) {
            MockResponse(412)
        } else {
            if (scheduling) schedule(current?.ics, body)
            MockResponse(
                if (current ==
                    null
                ) {
                    201
                } else {
                    204
                },
                headersOf("ETag", put(path, body))
            )
        }
    }

    private fun unfolded(ics: String) = ics.replace(Regex("\r?\n[ \t]"), "")

    private fun schedule(was: String?, now: String) {
        val before = was?.let(::unfolded)
        val after = unfolded(now)
        val organizer = property("ORGANIZER", after)?.substringAfter("mailto:")?.lowercase()
        val mine = addresses.map { it.lowercase() }
        val guests = ATTENDEE.findAll(after).map { it.groupValues[1].lowercase() to it.value }
        if (organizer in mine) {
            guests.filter { (address, _) -> address != organizer }.forEach { (address, line) ->
                val known = before?.contains("mailto:$address", ignoreCase = true) == true
                if (!known && "NEEDS-ACTION" in line) schedulingLog += "INVITE $address"
            }
        } else if (organizer != null) {
            val answer = guests.firstOrNull { it.first in mine }?.let { status(it.second) }
            val was = before?.let { old ->
                ATTENDEE.findAll(old).firstOrNull { it.groupValues[1].lowercase() in mine }
                    ?.let { status(it.value) }
            }
            if (answer != null && answer != was) schedulingLog += "REPLY $answer"
        }
    }

    private fun property(name: String, ics: String) = Regex(
        "^$name[^:\\r\\n]*:(.*)$",
        RegexOption.MULTILINE
    ).find(ics)?.groupValues?.get(1)?.trim()

    private fun status(line: String) = Regex("PARTSTAT=([A-Z-]+)").find(line)?.groupValues?.get(1)

    /** Every PROPPATCH body received, by calendar. */
    val proppatches = mutableListOf<Pair<String, String>>()

    private fun createCalendar(path: String, body: String): MockResponse {
        calendars[path] = DISPLAY_NAME.find(body)?.groupValues?.get(1).orEmpty()
        return MockResponse(201)
    }

    private fun patchCalendar(path: String, body: String): MockResponse {
        if (path !in calendars) return MockResponse(404)
        proppatches += path to body
        DISPLAY_NAME.find(body)?.let { calendars[path] = it.groupValues[1] }
        return multistatus()
    }

    private fun delete(path: String): MockResponse = when {
        resources.containsKey(path) -> MockResponse(204).also { remove(path) }

        calendars.remove(path) != null -> MockResponse(204).also {
            resources.keys.removeAll { it.startsWith(path) }
        }

        else -> MockResponse(404)
    }

    private fun move(path: String, destination: String): MockResponse {
        val resource = resources[path] ?: return MockResponse(404)
        val to = destination.substringAfter("://").substringAfter('/').let { "/$it" }
        remove(path)
        resources[to] = resource
        log += ++version to to
        return MockResponse(201)
    }

    private fun state(href: String) =
        resources[href]?.let { response(href, "<d:getetag>${it.etag}</d:getetag>") } ?: gone(href)

    private fun gone(href: String) =
        "<d:response><d:href>$href</d:href><d:status>HTTP/1.1 404 Not Found</d:status></d:response>"

    private fun response(href: String, props: String) =
        "<d:response><d:href>$href</d:href><d:propstat><d:prop>$props</d:prop>" +
            "<d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"

    private fun multistatus(vararg responses: String, token: String? = null) = MockResponse(
        207,
        headersOf("Content-Type", "application/xml"),
        "<d:multistatus xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\" " +
            "xmlns:cs=\"http://calendarserver.org/ns/\">" +
            responses.joinToString("") +
            (token?.let { "<d:sync-token>$it</d:sync-token>" } ?: "") + "</d:multistatus>"
    )

    private fun escape(text: String) =
        text.replace("&", "&amp;").replace("<", "&lt;").replace("\r", "&#13;")

    private companion object {
        val DISPLAY_NAME = Regex("<d:displayname>([^<]*)</d:displayname>")
        val ATTENDEE = Regex("^ATTENDEE[^\\r\\n]*mailto:([^\\r\\n;]+)", RegexOption.MULTILINE)
    }
}
