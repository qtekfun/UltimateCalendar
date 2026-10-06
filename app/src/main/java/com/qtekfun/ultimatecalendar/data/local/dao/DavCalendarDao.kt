// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.qtekfun.ultimatecalendar.data.local.entity.DavCalendarEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DavCalendarDao {
    @Insert
    suspend fun insert(calendar: DavCalendarEntity): Long

    @Update
    suspend fun update(calendar: DavCalendarEntity)

    /**
     * Inserts a new calendar or updates the one with the same href in place. Unlike REPLACE, the
     * row keeps its id, so its events are kept. Returns the saved calendar.
     */
    @Transaction
    suspend fun upsert(calendar: DavCalendarEntity): DavCalendarEntity {
        val old = byHref(calendar.accountId, calendar.href)
            ?: return calendar.copy(id = insert(calendar))
        return calendar.copy(id = old.id).also { update(it) }
    }

    @Query("SELECT * FROM dav_calendar WHERE id = :id")
    suspend fun get(id: Long): DavCalendarEntity?

    @Query("SELECT * FROM dav_calendar WHERE accountId = :accountId AND href = :href")
    suspend fun byHref(accountId: Long, href: String): DavCalendarEntity?

    @Query("SELECT * FROM dav_calendar WHERE accountId = :accountId")
    suspend fun all(accountId: Long): List<DavCalendarEntity>

    /** Calendars in the order of the drawer: server order, calendars without one last, then name. */
    @Query(
        "SELECT * FROM dav_calendar WHERE accountId = :accountId " +
            "ORDER BY sortOrder IS NULL, sortOrder, name COLLATE NOCASE"
    )
    fun observeAll(accountId: Long): Flow<List<DavCalendarEntity>>

    /** Where the next incremental pull of the calendar starts. */
    @Query("UPDATE dav_calendar SET syncToken = :token WHERE id = :id")
    suspend fun setSyncToken(id: Long, token: String?)

    /** The ctag of the calendar when it was last pulled, to skip calendars without changes. */
    @Query("UPDATE dav_calendar SET ctag = :ctag WHERE id = :id")
    suspend fun setCtag(id: Long, ctag: String?)

    @Query("DELETE FROM dav_calendar WHERE id = :id")
    suspend fun delete(id: Long)
}
