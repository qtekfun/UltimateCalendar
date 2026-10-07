// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import android.content.Context
import android.content.SharedPreferences
import com.qtekfun.ultimatecalendar.data.invitations.AttendedEventRecord
import com.qtekfun.ultimatecalendar.data.invitations.AttendedEvents
import com.qtekfun.ultimatecalendar.data.invitations.InvitationResponses
import com.qtekfun.ultimatecalendar.data.invitations.NotifiedInvitations
import com.qtekfun.ultimatecalendar.data.invitations.OwnEditMarks
import com.qtekfun.ultimatecalendar.data.invitations.SourceInvitationResponses
import com.qtekfun.ultimatecalendar.data.settings.RepositoryInvitationCheckSettings
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.OwnAddresses
import com.qtekfun.ultimatecalendar.data.sync.AccountSyncTrigger
import com.qtekfun.ultimatecalendar.data.sync.AndroidSyncEnvironment
import com.qtekfun.ultimatecalendar.data.sync.CompositeSyncRequester
import com.qtekfun.ultimatecalendar.data.sync.ContentResolverSyncTrigger
import com.qtekfun.ultimatecalendar.data.sync.PreferencesSyncRequestLog
import com.qtekfun.ultimatecalendar.data.sync.SourceSyncRequester
import com.qtekfun.ultimatecalendar.data.sync.SyncEnvironment
import com.qtekfun.ultimatecalendar.data.sync.SyncRequestLog
import com.qtekfun.ultimatecalendar.domain.invitations.ChangeNotifications
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationNotifier
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationReReminders
import com.qtekfun.ultimatecalendar.notify.AndroidInvitationNotifications
import com.qtekfun.ultimatecalendar.notify.ChangeNotificationSettings
import com.qtekfun.ultimatecalendar.notify.InvitationNotificationSurface
import com.qtekfun.ultimatecalendar.notify.InvitationRecheck
import com.qtekfun.ultimatecalendar.notify.SystemInvitationNotifier
import com.qtekfun.ultimatecalendar.sync.InvitationCheckScheduler
import com.qtekfun.ultimatecalendar.sync.InvitationCheckSettings
import com.qtekfun.ultimatecalendar.sync.InvitationChecker
import com.qtekfun.ultimatecalendar.sync.WorkManagerInvitationScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Named
import javax.inject.Provider
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
    fun syncEnvironment(environment: AndroidSyncEnvironment): SyncEnvironment

    @Binds
    fun syncRequestLog(log: PreferencesSyncRequestLog): SyncRequestLog

    @Binds
    fun syncTrigger(trigger: ContentResolverSyncTrigger): AccountSyncTrigger

    @Binds
    fun scheduler(scheduler: WorkManagerInvitationScheduler): InvitationCheckScheduler
}

/** The record of the events the user goes to (RF-07), for the check and for the app's writes. */
@Module
@InstallIn(SingletonComponent::class)
interface AttendedEventsBindingsModule {
    @Binds
    fun attendedRecord(events: AttendedEvents): AttendedEventRecord

    @Binds
    fun ownEditMarks(events: AttendedEvents): OwnEditMarks
}

@Module
@InstallIn(SingletonComponent::class)
object InvitationCheckModule {
    /** Where the time of the last sync request per account is kept. */
    @Provides
    @Named(PreferencesSyncRequestLog.FILE)
    fun syncRequestPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(PreferencesSyncRequestLog.FILE, Context.MODE_PRIVATE)

    /** The optional notifications of Settings (RF-07), read when a check notifies. */
    @Provides
    fun changeNotificationSettings(settings: SettingsRepository) = ChangeNotificationSettings {
        settings.current().let { ChangeNotifications(it.notifyChanges, it.notifyCancellations) }
    }

    /** The user's aliases, so that an invitation sent to one of them can be answered. */
    @Provides
    fun ownAddresses(settings: InvitationCheckSettings) = OwnAddresses { settings.aliases() }

    /** After an answer from a notification: look again, with no sync request of its own. */
    @Provides
    fun recheck(checker: Provider<InvitationChecker>) = InvitationRecheck {
        checker.get().check(requestSync = false).let { }
    }

    @Provides
    @Singleton
    @Suppress("LongParameterList")
    fun checker(
        source: CalendarSource,
        syncRequester: SourceSyncRequester,
        notified: NotifiedInvitations,
        notifier: InvitationNotifier,
        settings: InvitationCheckSettings,
        clock: Clock,
        @IoDispatcher io: CoroutineDispatcher,
        reReminders: InvitationReReminders,
        attended: AttendedEventRecord
    ): InvitationChecker = InvitationChecker(
        source,
        syncRequester,
        notified,
        notifier,
        settings,
        clock,
        io,
        reReminders,
        attended
    )
}
