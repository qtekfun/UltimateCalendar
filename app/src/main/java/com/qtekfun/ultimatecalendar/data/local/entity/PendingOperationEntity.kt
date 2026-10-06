// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.qtekfun.ultimatecalendar.data.local.model.OperationType

/**
 * A change made offline, waiting to reach the server. Survives restarts. Times are epoch
 * milliseconds. Only event changes are queued: creating, renaming or deleting calendars needs
 * a connection.
 */
@Entity(
    tableName = "pending_operation",
    foreignKeys = [
        ForeignKey(
            entity = DavAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId", "nextAttemptAt"), Index("accountId", "eventId")]
)
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val type: OperationType,
    val eventId: Long,
    val payload: String = "",
    /** What a newer operation of the same kind replaces, or null when nothing is. */
    val slot: String? = null,
    val createdAt: Long,
    val attempts: Int = 0,
    val nextAttemptAt: Long = createdAt,
    val lastError: String? = null,
    /** Refused for good by the server: no automatic retries until the user retries or discards it. */
    val failed: Boolean = false,
    /** When it was last handed to the server; it may have arrived even without an answer. */
    val startedAt: Long? = null
)
