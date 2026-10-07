// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.data.invitations.OwnEditMarks
import com.qtekfun.ultimatecalendar.data.invitations.ReplyStatus
import com.qtekfun.ultimatecalendar.data.invitations.SourceReplyStatus
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.CompositeCalendarSource
import com.qtekfun.ultimatecalendar.data.source.OwnEditMarkingSource
import com.qtekfun.ultimatecalendar.data.source.ProviderAccess
import com.qtekfun.ultimatecalendar.data.source.ProviderCalendarSource
import com.qtekfun.ultimatecalendar.data.source.ReplyRetryingSource
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavCalendarSource
import com.qtekfun.ultimatecalendar.data.source.caldav.CalDavSyncTrigger
import com.qtekfun.ultimatecalendar.data.source.caldav.RandomUidFactory
import com.qtekfun.ultimatecalendar.data.source.caldav.UidFactory
import com.qtekfun.ultimatecalendar.data.source.provider.ContentResolverGateway
import com.qtekfun.ultimatecalendar.data.source.provider.ProviderGateway
import com.qtekfun.ultimatecalendar.data.source.subscription.SubscriptionCalendarSource
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionScheduler
import com.qtekfun.ultimatecalendar.data.sync.ReplyDelivery
import com.qtekfun.ultimatecalendar.data.sync.ReplyRetry
import com.qtekfun.ultimatecalendar.sync.CalDavSyncScheduler
import com.qtekfun.ultimatecalendar.sync.WorkManagerCalDavScheduler
import com.qtekfun.ultimatecalendar.sync.WorkManagerReplyRetry
import com.qtekfun.ultimatecalendar.sync.WorkManagerSubscriptionScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The calendar source the app uses: the Android calendar provider, the app's own CalDAV calendars
 * and the read-only subscriptions, side by side (see [CompositeCalendarSource]).
 */
@Module
@InstallIn(SingletonComponent::class)
interface CalendarSourceModule {
    @Binds
    @Singleton
    fun gateway(gateway: ContentResolverGateway): ProviderGateway

    @Binds
    fun replyRetry(retry: WorkManagerReplyRetry): ReplyRetry

    @Binds
    fun replyStatus(status: SourceReplyStatus): ReplyStatus

    @Binds
    fun uids(factory: RandomUidFactory): UidFactory

    @Binds
    fun scheduler(scheduler: WorkManagerCalDavScheduler): CalDavSyncScheduler

    @Binds
    fun trigger(scheduler: WorkManagerCalDavScheduler): CalDavSyncTrigger

    @Binds
    fun subscriptionScheduler(scheduler: WorkManagerSubscriptionScheduler): SubscriptionScheduler

    companion object {
        @Provides
        @Singleton
        fun source(
            provider: ProviderCalendarSource,
            caldav: CalDavCalendarSource,
            subscriptions: SubscriptionCalendarSource
        ): CompositeCalendarSource = CompositeCalendarSource(provider, caldav, subscriptions)

        @Provides
        @Singleton
        fun calendarSource(
            composite: CompositeCalendarSource,
            ownEdits: OwnEditMarks,
            delivery: ReplyDelivery
        ): CalendarSource = OwnEditMarkingSource(ReplyRetryingSource(composite, delivery), ownEdits)

        @Provides
        fun providerAccess(composite: CompositeCalendarSource): ProviderAccess = composite
    }
}
