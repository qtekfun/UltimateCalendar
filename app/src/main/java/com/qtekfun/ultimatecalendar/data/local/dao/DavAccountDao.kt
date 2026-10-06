// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity

@Dao
interface DavAccountDao {
    @Insert
    suspend fun insert(account: DavAccountEntity): Long

    @Update
    suspend fun update(account: DavAccountEntity)

    @Query("SELECT * FROM dav_account WHERE id = :id")
    suspend fun get(id: Long): DavAccountEntity?

    @Query("SELECT * FROM dav_account WHERE serverUrl = :serverUrl AND loginName = :loginName")
    suspend fun find(serverUrl: String, loginName: String): DavAccountEntity?

    /** Removes the account and, through foreign keys, all its calendars, events and queue. */
    @Query("DELETE FROM dav_account WHERE id = :id")
    suspend fun delete(id: Long)
}
