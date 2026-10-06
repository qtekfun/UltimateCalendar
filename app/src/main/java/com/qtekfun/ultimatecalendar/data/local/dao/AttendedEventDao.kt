// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.qtekfun.ultimatecalendar.data.local.entity.AttendedEventEntity

@Dao
abstract class AttendedEventDao {
    @Query("SELECT * FROM attended_events")
    abstract suspend fun all(): List<AttendedEventEntity>

    @Query("DELETE FROM attended_events")
    protected abstract suspend fun clear()

    @Insert
    protected abstract suspend fun insert(events: List<AttendedEventEntity>)

    /** Swaps the whole set in one transaction: a run that dies halfway leaves the old one. */
    @Transaction
    open suspend fun replaceAll(events: List<AttendedEventEntity>) {
        clear()
        insert(events)
    }

    /** Flags (or unflags) the event as being changed by this app; nothing if it is not followed. */
    @Query("UPDATE attended_events SET ownEdit = :ownEdit WHERE eventId = :eventId")
    abstract suspend fun markOwnEdit(eventId: Long, ownEdit: Boolean)
}
