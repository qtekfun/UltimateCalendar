// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.qtekfun.ultimatecalendar.data.local.entity.ReRemindEntity

@Dao
abstract class ReRemindDao {
    @Query("SELECT * FROM invitation_re_reminders")
    abstract suspend fun all(): List<ReRemindEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun save(rows: List<ReRemindEntity>)

    @Query(
        "DELETE FROM invitation_re_reminders WHERE calendarId = :calendarId AND " +
            "eventId = :eventId AND address = :address AND moment = :moment AND start = :start"
    )
    protected abstract suspend fun delete(
        calendarId: Long,
        eventId: Long,
        address: String,
        moment: String,
        start: Long
    )

    /** Forgets [gone] and writes [rows] in one transaction: a run that dies halfway changes nothing. */
    @Transaction
    open suspend fun apply(rows: List<ReRemindEntity>, gone: List<ReRemindEntity>) {
        gone.forEach { delete(it.calendarId, it.eventId, it.address, it.moment, it.start) }
        save(rows)
    }
}
