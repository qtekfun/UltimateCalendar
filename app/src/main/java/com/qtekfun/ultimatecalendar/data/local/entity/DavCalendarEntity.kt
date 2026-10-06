// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A CalDAV calendar of the account, identified on the server by its [href]. [id] is what the
 * domain calls a `CalendarId` for this source.
 */
@Entity(
    tableName = "dav_calendar",
    foreignKeys = [
        ForeignKey(
            entity = DavAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId", "href", unique = true)]
)
data class DavCalendarEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val href: String,
    val name: String,
    /** `#RRGGBB`, from `calendar-color`; null when the server has none. */
    val color: String? = null,
    /** `calendar-order`; calendars without it go last. */
    val sortOrder: Int? = null,
    /** False for calendars shared read-only with the user. */
    val writable: Boolean = true,
    /** Where the next incremental pull starts (RFC 6578), or null to pull it all. */
    val syncToken: String? = null,
    /** `getctag` of the last pull, for servers without sync tokens. */
    val ctag: String? = null
)
