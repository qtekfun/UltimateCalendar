// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.entity

import androidx.room3.Entity

/**
 * The local name, color and visibility restored from a backup for a CalDAV calendar that does
 * not exist yet: it waits here, keyed by the account and the name the server will give the
 * calendar, until the first sync creates it (RF-11, RF-12). A null field means "the source's own".
 */
@Entity(tableName = "pending_calendar_override", primaryKeys = ["accountName", "calendarName"])
data class PendingCalendarOverrideEntity(
    val accountName: String,
    val calendarName: String,
    val displayName: String?,
    val color: Int?,
    val visible: Boolean?
)
