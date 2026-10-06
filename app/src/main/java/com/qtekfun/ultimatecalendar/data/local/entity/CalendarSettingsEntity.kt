// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * What only lives on this device for a calendar (RF-02): a name and a color that replace the
 * source's, and whether it is shown. A null field means "use the source's own value".
 */
@Entity(tableName = "calendar_settings")
data class CalendarSettingsEntity(
    @PrimaryKey val calendarId: Long,
    val displayName: String?,
    val color: Int?,
    val visible: Boolean?
)
