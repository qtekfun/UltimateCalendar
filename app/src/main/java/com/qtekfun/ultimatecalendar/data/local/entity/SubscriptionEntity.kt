// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A calendar the user subscribed to by address (T39). The address is a secret: only [urlSecret],
 * its Keystore-encrypted form, is stored, and [urlKey], a hash that finds a duplicate without
 * decrypting. [host] is what the UI may show. [etag] and [lastModified] are the validators of the
 * last download, for the conditional request. [refreshHours] is 0 for manual refresh only.
 * [error] is a `SubscriptionError` name, [errorCode] the HTTP status that goes with it.
 */
@Entity(tableName = "subscription", indices = [Index("urlKey", unique = true)])
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val enabled: Boolean = true,
    val refreshHours: Int,
    val host: String,
    val urlKey: String,
    val urlSecret: String,
    val etag: String? = null,
    val lastModified: String? = null,
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val error: String? = null,
    val errorCode: Int? = null,
    val eventCount: Int = 0,
    val skipped: Int = 0
)
