// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** The calendar chosen for new events: a table with at most one row, whose [id] is [ROW]. */
@Entity(tableName = "default_calendar")
data class DefaultCalendarEntity(@PrimaryKey val id: Int = ROW, val calendarId: Long) {
    companion object {
        const val ROW = 0
    }
}
