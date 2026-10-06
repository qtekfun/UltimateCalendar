// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import com.qtekfun.ultimatecalendar.data.local.dao.CalendarSettingsDao
import com.qtekfun.ultimatecalendar.data.local.dao.NotifiedInvitationDao
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DefaultCalendarEntity
import com.qtekfun.ultimatecalendar.data.local.entity.NotifiedInvitationEntity

@Database(
    entities = [
        CalendarSettingsEntity::class,
        DefaultCalendarEntity::class,
        NotifiedInvitationEntity::class
    ],
    version = UltimateCalendarDatabase.VERSION,
    exportSchema = true
)
abstract class UltimateCalendarDatabase : RoomDatabase() {
    abstract fun calendarSettingsDao(): CalendarSettingsDao

    abstract fun notifiedInvitationDao(): NotifiedInvitationDao

    companion object {
        const val VERSION = 2

        /**
         * Migrations from each released version to the next. There is no destructive fallback:
         * raising [VERSION] requires adding its migration here (checked by DatabaseSchemaTest).
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
