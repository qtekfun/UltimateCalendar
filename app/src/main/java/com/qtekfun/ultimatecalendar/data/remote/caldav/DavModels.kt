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

/**
 * What discovery finds about the account: where its calendars live, the addresses the server
 * knows the user by (`calendar-user-address-set`, `mailto:` ones, normalized) and the scheduling
 * outbox (RFC 6638), which only servers that schedule invitations have.
 */
data class DavProfile(
    val home: String,
    val addresses: List<String>,
    val schedulingOutbox: String?
) {
    /** The server sends invitations and answers for the user: needs an outbox and an address. */
    val schedules: Boolean get() = schedulingOutbox != null && addresses.isNotEmpty()
}
