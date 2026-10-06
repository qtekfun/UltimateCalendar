// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote.caldav

import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/** The CalDAV writes: events uploaded, deleted or moved, and calendars created, changed or deleted. */
class CalDavWrites(client: OkHttpClient, server: HttpUrl, io: CoroutineDispatcher) {
    private val http = DavHttp(client, server, io)

    /**
     * Uploads a event. Without [etag] it must be new (`If-None-Match: *`); with it, the server
     * copy must still be that version (`If-Match`). Either way a mismatch is
     * [DavResult.PreconditionFailed]. Returns the new ETag when the server sends one.
     */
    suspend fun put(href: String, ics: String, etag: String?): DavResult<String?> = http.send(
        "PUT",
        href,
        ics,
        if (etag == null) mapOf("If-None-Match" to "*") else mapOf("If-Match" to etag),
        DavHttp.CALENDAR
    ).map { it.etag }

    suspend fun delete(href: String, etag: String?): DavResult<Unit> =
        http.send("DELETE", href, headers = etag?.let { mapOf("If-Match" to it) }.orEmpty()).map { }

    /** Moves a event to another calendar without replacing anything already there. */
    suspend fun move(from: String, to: String, etag: String?): DavResult<Unit> {
        val headers = mapOf("Destination" to http.url(to).toString(), "Overwrite" to "F") +
            etag?.let { mapOf("If-Match" to it) }.orEmpty()
        return http.send("MOVE", from, headers = headers).map { }
    }

    /** Creates a calendar under [home] and returns its href. */
    suspend fun createCalendar(home: String, name: String, color: String?): DavResult<String> {
        val href = home.trimEnd('/') + "/" + UUID.randomUUID() + "/"
        return http.send("MKCALENDAR", href, DavXml.mkcalendar(name, color)).map { href }
    }

    /** Renames, recolors or reorders a calendar; null values are left as they are. */
    suspend fun updateCalendar(
        href: String,
        name: String?,
        color: String?,
        order: Int?
    ): DavResult<Unit> = http.send(
        "PROPPATCH",
        href,
        DavXml.proppatch(DavXml.calendarProperties(name, color, order))
    ).map {
    }

    suspend fun deleteCalendar(href: String): DavResult<Unit> = http.send("DELETE", href).map { }
}
