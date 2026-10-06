// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.data.invitations.InvitationResponses
import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.invitations.SourceInvitationResponses
import com.qtekfun.ultimatecalendar.data.settings.RepositoryInvitationCheckSettings
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.UnavailableCalendarSource
import com.qtekfun.ultimatecalendar.data.sync.AccountSyncTrigger
import com.qtekfun.ultimatecalendar.data.sync.CompositeSyncRequester
import com.qtekfun.ultimatecalendar.data.sync.ContentResolverSyncTrigger
import com.qtekfun.ultimatecalendar.data.sync.SourceSyncRequester
import com.qtekfun.ultimatecalendar.domain.invitations.ChangeNotifications
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationNotifier
import com.qtekfun.ultimatecalendar.notify.AndroidInvitationNotifications
import com.qtekfun.ultimatecalendar.notify.ChangeNotificationSettings
import com.qtekfun.ultimatecalendar.notify.InvitationNotificationSurface
import com.qtekfun.ultimatecalendar.notify.SystemInvitationNotifier
import com.qtekfun.ultimatecalendar.sync.InvitationCheckScheduler
import com.qtekfun.ultimatecalendar.sync.InvitationCheckSettings
import com.qtekfun.ultimatecalendar.sync.InvitationChecker
import com.qtekfun.ultimatecalendar.sync.WorkManagerInvitationScheduler
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.Optional
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The bindings of the invitation check (T08).
 */
@Module
@InstallIn(SingletonComponent::class)
interface InvitationCheckBindingsModule {
    @Binds
    fun notifier(notifier: SystemInvitationNotifier): InvitationNotifier

    @Binds
    fun notifications(notifications: AndroidInvitationNotifications): InvitationNotificationSurface

    @Binds
    fun responses(responses: SourceInvitationResponses): InvitationResponses

    @Binds
    fun settings(settings: RepositoryInvitationCheckSettings): InvitationCheckSettings

    @Binds
    fun syncRequester(requester: CompositeSyncRequester): SourceSyncRequester

    @Binds
    fun syncTrigger(trigger: ContentResolverSyncTrigger): AccountSyncTrigger

    @Binds
    fun scheduler(scheduler: WorkManagerInvitationScheduler): InvitationCheckScheduler

    /**
     * Present once T05 binds a [CalendarSource]; until then the graph still compiles, and the
     * check finds [UnavailableCalendarSource]. T05 may then inject the source straight into
     * `InvitationChecker` and drop this.
     */
    @BindsOptionalOf
    fun calendarSource(): CalendarSource
}

@Module
@InstallIn(SingletonComponent::class)
object InvitationCheckModule {
    /** The optional notifications of Settings (RF-07), read when a check notifies. */
    @Provides
    fun changeNotificationSettings(settings: SettingsRepository) = ChangeNotificationSettings {
        settings.current().let { ChangeNotifications(it.notifyChanges, it.notifyCancellations) }
    }

    @Provides
    @Singleton
    @Suppress("LongParameterList")
    fun checker(
        source: Optional<CalendarSource>,
        syncRequester: SourceSyncRequester,
        notified: NotifiedInvitations,
        notifier: InvitationNotifier,
        settings: InvitationCheckSettings,
        clock: Clock,
        @IoDispatcher io: CoroutineDispatcher
    ): InvitationChecker = InvitationChecker(
        source.orElse(UnavailableCalendarSource),
        syncRequester,
        notified,
        notifier,
        settings,
        clock,
        io
    )
}
