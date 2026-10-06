// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.entity

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
    val calendarHome: String? = null
)
