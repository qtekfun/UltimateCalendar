// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.qtekfun.ultimatecalendar.data.local.entity.NotifiedInvitationEntity

@Dao
abstract class NotifiedInvitationDao {
    @Query("SELECT * FROM notified_invitations")
    abstract suspend fun all(): List<NotifiedInvitationEntity>

    @Query("DELETE FROM notified_invitations")
    protected abstract suspend fun clear()

    @Insert
    protected abstract suspend fun insert(invitations: List<NotifiedInvitationEntity>)

    /** Swaps the whole set in one transaction: a run that dies halfway leaves the old one. */
    @Transaction
    open suspend fun replaceAll(invitations: List<NotifiedInvitationEntity>) {
        clear()
        insert(invitations)
    }
}
