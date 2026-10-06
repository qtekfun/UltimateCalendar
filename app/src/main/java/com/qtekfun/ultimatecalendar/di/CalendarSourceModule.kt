// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.CompositeCalendarSource
import com.qtekfun.ultimatecalendar.data.source.ProviderCalendarSource
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavCalendarSource
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavSyncTrigger
import com.qtekfun.ultimatecalendar.data.source.caldav.RandomUidFactory
import com.qtekfun.ultimatecalendar.data.source.caldav.UidFactory
import com.qtekfun.ultimatecalendar.data.source.provider.ContentResolverGateway
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderGateway
import com.qtekfun.ultimatecalendar.sync.CalDavSyncScheduler
import com.qtekfun.ultimatecalendar.sync.WorkManagerCalDavScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The calendar source the app uses: the Android calendar provider and the app's own CalDAV
 * calendars, side by side (see [CompositeCalendarSource]).
 */
@Module
@InstallIn(SingletonComponent::class)
interface CalendarSourceModule {
    @Binds
    @Singleton
    fun gateway(gateway: ContentResolverGateway): ProviderGateway

    @Binds
    fun uids(factory: RandomUidFactory): UidFactory

    @Binds
    fun scheduler(scheduler: WorkManagerCalDavScheduler): CalDavSyncScheduler

    @Binds
    fun trigger(scheduler: WorkManagerCalDavScheduler): CalDavSyncTrigger

    companion object {
        @Provides
        @Singleton
        fun source(provider: ProviderCalendarSource, caldav: CalDavCalendarSource): CalendarSource =
            CompositeCalendarSource(provider, caldav)
    }
}
