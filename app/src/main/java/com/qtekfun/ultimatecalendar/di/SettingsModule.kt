// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import android.content.Context
import android.content.SharedPreferences
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Named(SettingsRepository.SETTINGS_PREFERENCES)
    fun settingsPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(SettingsRepository.SETTINGS_PREFERENCES, Context.MODE_PRIVATE)

    /** Where robust mode was kept before the settings repository; read once to migrate it. */
    @Provides
    @Named(SettingsRepository.LEGACY_REMINDER_PREFERENCES)
    fun legacyReminderPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(
            SettingsRepository.LEGACY_REMINDER_PREFERENCES,
            Context.MODE_PRIVATE
        )

    /** Where the first-run flag was kept before the settings repository; read once to migrate it. */
    @Provides
    @Named(SettingsRepository.LEGACY_FIRST_RUN_PREFERENCES)
    fun legacyFirstRunPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(
            SettingsRepository.LEGACY_FIRST_RUN_PREFERENCES,
            Context.MODE_PRIVATE
        )
}
