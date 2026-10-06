// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import android.content.ContentResolver
import android.content.Context
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.data.source.ProviderCalendarPresence
import com.qtekfun.ultimatecalendar.domain.firstrun.CalendarPresence
import com.qtekfun.ultimatecalendar.domain.firstrun.FirstRunFlag
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface FirstRunBindingsModule {
    @Binds
    fun flag(settings: SettingsRepository): FirstRunFlag

    @Binds
    fun presence(presence: ProviderCalendarPresence): CalendarPresence
}

@Module
@InstallIn(SingletonComponent::class)
object ContentResolverModule {
    @Provides
    fun contentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver
}
