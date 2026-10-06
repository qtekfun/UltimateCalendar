// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote.caldav

/** A CalDAV collection that can hold events, as the server describes it. */
data class DavCollection(
    val href: String,
    val name: String,
    /** `#RRGGBB`; Nextcloud sends `#RRGGBBAA`, the alpha is dropped. */
    val color: String?,
    val order: Int?,
    val writable: Boolean,
    val syncToken: String?,
    val ctag: String?
)

/** A event resource: its path, ETag and, when fetched, its iCalendar text. */
data class DavResource(val href: String, val etag: String?, val data: String? = null)

/** What changed in a calendar since a sync token (RFC 6578). */
data class DavChanges(
    val changed: List<DavResource>,
    val deleted: List<String>,
    val syncToken: String?
)
