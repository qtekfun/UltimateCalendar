// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import com.qtekfun.ultimatecalendar.data.local.dao.CalendarSettingsDao
import com.qtekfun.ultimatecalendar.data.local.dao.DavAccountDao
import com.qtekfun.ultimatecalendar.data.local.dao.DavCalendarDao
import com.qtekfun.ultimatecalendar.data.local.dao.DavEventDao
import com.qtekfun.ultimatecalendar.data.local.dao.NotifiedInvitationDao
import com.qtekfun.ultimatecalendar.data.local.dao.PendingOperationDao
import com.qtekfun.ultimatecalendar.data.local.dao.PendingOperationRetryDao
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavCalendarEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavEventEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DefaultCalendarEntity
import com.qtekfun.ultimatecalendar.data.local.entity.NotifiedInvitationEntity
import com.qtekfun.ultimatecalendar.data.local.entity.PendingOperationEntity

@Database(
    entities = [
        CalendarSettingsEntity::class,
        DefaultCalendarEntity::class,
        NotifiedInvitationEntity::class,
        DavAccountEntity::class,
        DavCalendarEntity::class,
        DavEventEntity::class,
        PendingOperationEntity::class
    ],
    version = UltimateCalendarDatabase.VERSION,
    exportSchema = true
)
abstract class UltimateCalendarDatabase : RoomDatabase() {
    abstract fun calendarSettingsDao(): CalendarSettingsDao

    abstract fun notifiedInvitationDao(): NotifiedInvitationDao

    abstract fun davAccountDao(): DavAccountDao

    abstract fun davCalendarDao(): DavCalendarDao

    abstract fun davEventDao(): DavEventDao

    abstract fun pendingOperationDao(): PendingOperationDao

    abstract fun pendingOperationRetryDao(): PendingOperationRetryDao

    companion object {
        const val VERSION = 4

        /**
         * Migrations from each released version to the next. There is no destructive fallback:
         * raising [VERSION] requires adding its migration here (checked by DatabaseSchemaTest).
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
