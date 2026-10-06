// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** The CalDAV account whose calendars live in Room (RF-12). Credentials are kept apart. */
@Entity(tableName = "dav_account", indices = [Index("serverUrl", "loginName", unique = true)])
data class DavAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverUrl: String,
    val loginName: String,
    /** Where the account's calendars live, found by discovery. */
    val calendarHome: String? = null,
    /** The user's calendar addresses (`calendar-user-address-set`), normalized and `,`-joined. */
    @ColumnInfo(defaultValue = "") val userAddresses: String = "",
    /** The server schedules invitations and answers itself (RFC 6638), found by discovery. */
    @ColumnInfo(defaultValue = "0") val scheduling: Boolean = false
)
