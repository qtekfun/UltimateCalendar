// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarSettingsDao {
    @Query("SELECT * FROM calendar_settings")
    fun observeAll(): Flow<List<CalendarSettingsEntity>>

    @Query("SELECT * FROM calendar_settings")
    suspend fun all(): List<CalendarSettingsEntity>

    @Query("SELECT * FROM calendar_settings WHERE calendarId = :calendarId")
    suspend fun find(calendarId: Long): CalendarSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: CalendarSettingsEntity)

    @Query("DELETE FROM calendar_settings WHERE calendarId = :calendarId")
    suspend fun clear(calendarId: Long)
}
