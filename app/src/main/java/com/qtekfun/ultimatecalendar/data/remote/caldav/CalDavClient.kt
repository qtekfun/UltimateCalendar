// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote.caldav

import com.qtekfun.ultimatecalendar.data.remote.caldav.DavXml.CALDAV
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavXml.DAV
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * The CalDAV reads the app needs (SPEC RF-12): discovery, calendars, their
 * changes and event resources; [CalDavWrites] changes them. [client] already authenticates; hrefs are server paths.
 * [server] is the Nextcloud base URL, ending in `/`; it may live under a path (`/nextcloud/`).
 */
class CalDavClient(client: OkHttpClient, server: HttpUrl, io: CoroutineDispatcher) {
    private val http = DavHttp(client, server, io)

    /** The account's calendar home: `current-user-principal`, then `calendar-home-set`. */
    suspend fun discover(): DavResult<String> =
        propfind(DAV_ROOT, "<d:current-user-principal/>", depth = "0")
            .then { found(DavParsers.href(it, DAV, "current-user-principal")) }
            .then { principal -> propfind(principal, "<c:calendar-home-set/>", depth = "0") }
            .then { found(DavParsers.href(it, CALDAV, "calendar-home-set")) }

    /** The collections under [home] that can hold events. */
    suspend fun calendars(home: String): DavResult<List<DavCollection>> =
        propfind(home, CALENDAR_PROPERTIES, depth = "1").map(DavParsers::calendars)

    /** What changed in [calendar] since [syncToken]; a null token calendars every event. */
    suspend fun changes(calendar: String, syncToken: String?): DavResult<DavChanges> =
        report(calendar, DavXml.syncCollection(syncToken)).map(DavParsers::changes)

    /** Every event of [calendar] with its ETag, for servers without sync tokens. */
    suspend fun allEvents(calendar: String): DavResult<List<DavResource>> =
        report(calendar, DavXml.allEvents()).map(DavParsers::resources)

    /** The iCalendar text of [hrefs] in [calendar], in one request. */
    suspend fun fetch(calendar: String, hrefs: List<String>): DavResult<List<DavResource>> =
        report(calendar, DavXml.multiget(hrefs)).map(DavParsers::resources)

    private suspend fun propfind(href: String, props: String, depth: String) =
        multistatus(http.send("PROPFIND", href, DavXml.propfind(props), mapOf("Depth" to depth)))

    private suspend fun report(href: String, body: String) =
        multistatus(http.send("REPORT", href, body, mapOf("Depth" to "1")))

    private fun multistatus(result: DavResult<DavAnswer>): DavResult<Multistatus> =
        result.then { answer -> found(DavXml.multistatus(answer.body)) }

    private fun <T> found(value: T?): DavResult<T> =
        value?.let { DavResult.Success(it) } ?: DavResult.ParseError

    private companion object {
        const val DAV_ROOT = "remote.php/dav/"
        const val CALENDAR_PROPERTIES =
            "<d:displayname/><d:resourcetype/><c:supported-calendar-component-set/>" +
                "<a:calendar-color/><a:calendar-order/><cs:getctag/><d:sync-token/><d:current-user-privilege-set/>"
    }
}
