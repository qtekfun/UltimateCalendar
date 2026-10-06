// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.qtekfun.ultimatecalendar.data.local.entity.DavEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DavEventDao {
    @Insert
    suspend fun insert(event: DavEventEntity): Long

    @Update
    suspend fun update(event: DavEventEntity)

    @Query("SELECT * FROM dav_event WHERE id = :id")
    suspend fun get(id: Long): DavEventEntity?

    @Query("SELECT * FROM dav_event WHERE accountId = :accountId AND href = :href")
    suspend fun byHref(accountId: Long, href: String): DavEventEntity?

    /** Every event of a calendar, deleted ones included, as the sync needs them. */
    @Query("SELECT * FROM dav_event WHERE calendarId = :calendarId")
    suspend fun inCalendar(calendarId: Long): List<DavEventEntity>

    /**
     * The series that may have an occurrence between [from] and [to] (epoch milliseconds): the
     * ones the range can touch by their bounds; expanding them is the recurrence engine's job.
     * Deleted events are already gone for the user.
     */
    @Query(
        "SELECT * FROM dav_event WHERE calendarId IN (:calendarIds) AND NOT deleted " +
            "AND windowStart < :to AND (windowEnd IS NULL OR windowEnd > :from) " +
            "ORDER BY windowStart, id"
    )
    fun observeWindow(calendarIds: List<Long>, from: Long, to: Long): Flow<List<DavEventEntity>>

    @Query("DELETE FROM dav_event WHERE id = :id")
    suspend fun delete(id: Long)
}
