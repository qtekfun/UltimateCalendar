// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.qtekfun.ultimatecalendar.data.local.entity.SubscriptionEventEntity

@Dao
interface SubscriptionEventDao {
    @Insert
    suspend fun insert(event: SubscriptionEventEntity): Long

    @Update
    suspend fun update(event: SubscriptionEventEntity)

    @Query("SELECT * FROM subscription_event WHERE id = :id")
    suspend fun get(id: Long): SubscriptionEventEntity?

    @Query("SELECT * FROM subscription_event WHERE subscriptionId = :subscriptionId")
    suspend fun inSubscription(subscriptionId: Long): List<SubscriptionEventEntity>

    @Query("DELETE FROM subscription_event WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * The series that may have an occurrence between [from] and [to] (epoch milliseconds): the
     * ones the range can touch by their bounds; expanding them is the recurrence engine's job.
     */
    @Query(
        "SELECT * FROM subscription_event WHERE subscriptionId IN (:subscriptionIds) " +
            "AND windowStart < :to AND (windowEnd IS NULL OR windowEnd > :from) " +
            "ORDER BY windowStart, id"
    )
    suspend fun inWindow(
        subscriptionIds: List<Long>,
        from: Long,
        to: Long
    ): List<SubscriptionEventEntity>

    /**
     * The events whose text fields may contain what [pattern] (a `LIKE` pattern escaped with `\`)
     * looks for: a coarse filter, the search decides.
     */
    @Query(
        "SELECT * FROM subscription_event WHERE subscriptionId IN (:subscriptionIds) AND " +
            "(title LIKE :pattern ESCAPE '\\' OR location LIKE :pattern ESCAPE '\\' " +
            "OR description LIKE :pattern ESCAPE '\\' OR organizer LIKE :pattern ESCAPE '\\') " +
            "ORDER BY id"
    )
    suspend fun matching(
        subscriptionIds: List<Long>,
        pattern: String
    ): List<SubscriptionEventEntity>
}
