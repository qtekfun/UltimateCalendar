// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query

/**
 * What is keyed by calendar outside the account's own tables and has to go with the account
 * when it is signed out (T37): the local overrides and the invitations
 * noticed and their re-reminders. [calendarIds] are the app's calendar ids (see `CalDavIds`).
 */
@Dao
interface AccountCleanupDao {
    @Query("DELETE FROM calendar_settings WHERE calendarId IN (:calendarIds)")
    suspend fun clearSettings(calendarIds: List<Long>)

    @Query("DELETE FROM notified_invitations WHERE calendarId IN (:calendarIds)")
    suspend fun clearNotified(calendarIds: List<Long>)

    @Query("DELETE FROM invitation_re_reminders WHERE calendarId IN (:calendarIds)")
    suspend fun clearReReminders(calendarIds: List<Long>)
}
