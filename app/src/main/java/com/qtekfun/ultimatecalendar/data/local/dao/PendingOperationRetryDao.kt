// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import com.qtekfun.ultimatecalendar.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

/** Retry state of queued operations: postponed after a temporary error or failed for good. */
@Dao
interface PendingOperationRetryDao {
    @Query(
        "UPDATE pending_operation SET attempts = attempts + 1, nextAttemptAt = :nextAttemptAt, " +
            "lastError = :error WHERE id = :id"
    )
    suspend fun recordFailure(id: Long, nextAttemptAt: Long, error: String?)

    @Query(
        "UPDATE pending_operation SET failed = 1, attempts = attempts + 1, lastError = :error " +
            "WHERE id = :id"
    )
    suspend fun markFailed(id: Long, error: String?)

    /** Recorded before each run: if the app dies mid-request, the next run knows it may have arrived. */
    @Query("UPDATE pending_operation SET startedAt = :at WHERE id = :id")
    suspend fun markStarted(id: Long, at: Long)

    /** Makes a failed or postponed operation run at [now]. */
    @Query("UPDATE pending_operation SET failed = 0, nextAttemptAt = :now WHERE id = :id")
    suspend fun resetForRetry(id: Long, now: Long)

    @Query(
        "SELECT * FROM pending_operation WHERE accountId = :accountId AND failed = 1 ORDER BY id"
    )
    fun observeFailed(accountId: Long): Flow<List<PendingOperationEntity>>
}
