// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.dao.AttendedEventDao
import com.qtekfun.ultimatecalendar.data.local.dao.CalendarSettingsDao
import com.qtekfun.ultimatecalendar.data.local.dao.NotifiedInvitationDao
import com.qtekfun.ultimatecalendar.data.local.dao.ReRemindDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/** Provides the database; repositories take the DAOs they need. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "ultimatecalendar.db"

    // The spread copies a tiny array once, when the database is created.
    @Suppress("SpreadOperator")
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): UltimateCalendarDatabase =
        Room.databaseBuilder<UltimateCalendarDatabase>(context, DATABASE_NAME)
            // The system SQLite keeps the APK small; tests use the bundled build, same API.
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(ioDispatcher)
            .addMigrations(*UltimateCalendarDatabase.MIGRATIONS)
            .build()

    @Provides
    fun calendarSettingsDao(database: UltimateCalendarDatabase): CalendarSettingsDao =
        database.calendarSettingsDao()

    @Provides
    fun notifiedInvitationDao(database: UltimateCalendarDatabase): NotifiedInvitationDao =
        database.notifiedInvitationDao()

    @Provides
    fun attendedEventDao(database: UltimateCalendarDatabase): AttendedEventDao =
        database.attendedEventDao()

    @Provides
    fun reRemindDao(database: UltimateCalendarDatabase): ReRemindDao = database.reRemindDao()
}
