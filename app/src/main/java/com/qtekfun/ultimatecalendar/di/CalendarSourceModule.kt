// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.ProviderCalendarSource
import com.qtekfun.ultimatecalendar.data.source.provider.ContentResolverGateway
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The calendar source the app uses: the Android calendar provider (CalDAV joins in phase 6). */
@Module
@InstallIn(SingletonComponent::class)
interface CalendarSourceModule {
    @Binds
    @Singleton
    fun source(source: ProviderCalendarSource): CalendarSource

    @Binds
    @Singleton
    fun gateway(gateway: ContentResolverGateway): ProviderGateway
}
