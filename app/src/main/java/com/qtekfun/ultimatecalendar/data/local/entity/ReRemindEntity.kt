// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.entity

import androidx.room3.Entity

/**
 * A re-reminder of an unanswered invitation (T40): which invitation, which [moment]
 * (`DAY_BEFORE` or `HOUR_BEFORE`) and which occurrence ([start]: epoch milliseconds for a timed
 * event, epoch day for an all-day one). [at] is when it goes off, in epoch milliseconds, and
 * [settled] tells that it already showed (or was past when first seen), so it never shows again.
 */
@Entity(
    tableName = "invitation_re_reminders",
    primaryKeys = ["calendarId", "eventId", "moment", "start"]
)
data class ReRemindEntity(
    val calendarId: Long,
    val eventId: Long,
    val moment: String,
    val start: Long,
    val at: Long,
    val settled: Boolean
)
