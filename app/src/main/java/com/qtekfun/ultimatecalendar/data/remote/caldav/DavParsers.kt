// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote.caldav

import com.qtekfun.ultimatecalendar.data.remote.caldav.DavXml.APPLE
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavXml.CALDAV
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavXml.CALENDARSERVER
import com.qtekfun.ultimatecalendar.data.remote.caldav.DavXml.DAV
import com.qtekfun.ultimatecalendar.domain.model.Attendee

/** Turns multistatus answers into the client's models. */
internal object DavParsers {
    private val WRITE_PRIVILEGES = setOf("all", "write", "write-content")
    private val COLOR = Regex("#[0-9A-Fa-f]{6}")
    private const val GONE = 404
    private const val MAILTO = "mailto:"

    /** The href inside the first `<d:href>` of [property] of the first response. */
    fun href(multistatus: Multistatus, namespace: String, property: String): String? =
        multistatus.responses.firstNotNullOfOrNull {
            it.property(namespace, property)?.child(DAV, "href")?.textContent?.trim()
        }

    /** The user's `mailto:` addresses of the first response, normalized, without repeats. */
    fun addresses(multistatus: Multistatus): List<String> = multistatus.responses
        .firstNotNullOfOrNull { it.property(CALDAV, "calendar-user-address-set") }
        ?.children(DAV, "href").orEmpty()
        .map { it.textContent.trim() }
        .filter { it.startsWith(MAILTO, ignoreCase = true) }
        .map { Attendee.normalize(it.substring(MAILTO.length)) }
        .filter { it.isNotEmpty() }
        .distinct()

    /** Calendars that can hold events; other collections (address books, trash…) are skipped. */
    fun calendars(multistatus: Multistatus): List<DavCollection> = multistatus.responses
        .filter { response ->
            val types = response.property(DAV, "resourcetype")?.elements().orEmpty()
            types.any { it.namespaceURI == CALDAV && it.localName == "calendar" } &&
                holdsEvents(response)
        }
        .map { response ->
            DavCollection(
                href = response.href,
                name =
                    response.text(DAV, "displayname")
                        ?: response.href.trimEnd('/').substringAfterLast('/'),
                color = response.text(APPLE, "calendar-color")?.let {
                    COLOR.find(it)?.value?.uppercase()
                },
                order = response.text(APPLE, "calendar-order")?.toIntOrNull(),
                writable = writable(response),
                syncToken = response.text(DAV, "sync-token"),
                ctag = response.text(CALENDARSERVER, "getctag")
            )
        }

    /** A calendar without a declared component set accepts every component, events included. */
    private fun holdsEvents(response: DavResponse): Boolean {
        val set = response.property(CALDAV, "supported-calendar-component-set") ?: return true
        return set.children(CALDAV, "comp").any {
            it.getAttribute("name").equals("VEVENT", ignoreCase = true)
        }
    }

    /** Without a privilege set the server is assumed to allow writing. */
    private fun writable(response: DavResponse): Boolean {
        val set = response.property(DAV, "current-user-privilege-set") ?: return true
        return set.children(DAV, "privilege").flatMap { it.elements() }.any {
            it.localName in
                WRITE_PRIVILEGES
        }
    }

    fun changes(multistatus: Multistatus) = DavChanges(
        changed = multistatus.responses.filter { it.status != GONE }.map(::resource),
        deleted = multistatus.responses.filter { it.status == GONE }.map { it.href },
        syncToken = multistatus.syncToken
    )

    fun resources(multistatus: Multistatus): List<DavResource> =
        multistatus.responses.filter { it.status != GONE }.map(::resource)

    private fun resource(response: DavResponse) = DavResource(
        href = response.href,
        etag = response.text(DAV, "getetag"),
        data = response.property(CALDAV, "calendar-data")?.textContent?.takeIf { it.isNotBlank() }
    )
}
