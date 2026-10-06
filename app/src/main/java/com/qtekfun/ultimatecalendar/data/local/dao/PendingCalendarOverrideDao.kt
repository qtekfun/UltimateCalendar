// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.qtekfun.ultimatecalendar.data.local.entity.PendingCalendarOverrideEntity

/** The overrides of CalDAV calendars that wait for their first sync. */
@Dao
interface PendingCalendarOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(rows: List<PendingCalendarOverrideEntity>)

    @Query("SELECT * FROM pending_calendar_override WHERE accountName = :accountName")
    suspend fun forAccount(accountName: String): List<PendingCalendarOverrideEntity>

    @Query(
        "DELETE FROM pending_calendar_override" +
            " WHERE accountName = :accountName AND calendarName = :calendarName"
    )
    suspend fun delete(accountName: String, calendarName: String)

    @Query("SELECT * FROM pending_calendar_override")
    suspend fun all(): List<PendingCalendarOverrideEntity>
}
