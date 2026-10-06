// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.qtekfun.ultimatecalendar.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

/** Storage of the operation queue; ordering, merging and retries live in `OperationQueue`. */
@Dao
interface PendingOperationDao {
    @Insert
    suspend fun insert(operation: PendingOperationEntity): Long

    /** Operations in the order they were made, which is the order they must reach the server. */
    @Query("SELECT * FROM pending_operation WHERE accountId = :accountId ORDER BY id")
    suspend fun all(accountId: Long): List<PendingOperationEntity>

    @Query(
        "SELECT * FROM pending_operation WHERE accountId = :accountId AND eventId = :eventId " +
            "ORDER BY id"
    )
    suspend fun forEvent(accountId: Long, eventId: Long): List<PendingOperationEntity>

    @Query("DELETE FROM pending_operation WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_operation WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM pending_operation WHERE accountId = :accountId AND eventId = :eventId")
    suspend fun deleteForEvent(accountId: Long, eventId: Long)

    @Query("SELECT COUNT(*) FROM pending_operation WHERE accountId = :accountId")
    fun observeCount(accountId: Long): Flow<Int>
}
