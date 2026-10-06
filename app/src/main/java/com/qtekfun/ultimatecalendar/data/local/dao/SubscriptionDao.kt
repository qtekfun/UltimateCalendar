// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.qtekfun.ultimatecalendar.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Insert
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscription WHERE id = :id")
    suspend fun get(id: Long): SubscriptionEntity?

    @Query("SELECT * FROM subscription WHERE urlKey = :urlKey")
    suspend fun byUrlKey(urlKey: String): SubscriptionEntity?

    @Query("SELECT * FROM subscription ORDER BY name COLLATE NOCASE, id")
    suspend fun all(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscription ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    /** Removes the subscription; its events go with it. */
    @Query("DELETE FROM subscription WHERE id = :id")
    suspend fun delete(id: Long)
}
