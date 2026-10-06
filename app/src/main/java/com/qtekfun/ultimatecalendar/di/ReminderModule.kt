// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.data.reminders.CalendarReminderEventSource
import com.qtekfun.ultimatecalendar.data.settings.RepositoryReminderSettings
import com.qtekfun.ultimatecalendar.domain.invitations.InvitationReReminders
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderEventSource
import com.qtekfun.ultimatecalendar.notify.AndroidInvitationReReminderAlarms
import com.qtekfun.ultimatecalendar.notify.InvitationReReminderAlarms
import com.qtekfun.ultimatecalendar.notify.PreferencesShownReminders
import com.qtekfun.ultimatecalendar.notify.PreferencesSnoozedReminders
import com.qtekfun.ultimatecalendar.notify.ReRemindCoordinator
import com.qtekfun.ultimatecalendar.notify.ReminderBeat
import com.qtekfun.ultimatecalendar.notify.ReminderHeartbeat
import com.qtekfun.ultimatecalendar.notify.ReminderSettingsSource
import com.qtekfun.ultimatecalendar.notify.ShownReminders
import com.qtekfun.ultimatecalendar.notify.SnoozedReminders
import com.qtekfun.ultimatecalendar.notify.SystemZone
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId

@Module
@InstallIn(SingletonComponent::class)
interface ReminderBindingsModule {
    /** The occurrences of the app's one calendar source, with their reminders (RF-08). */
    @Binds
    fun events(source: CalendarReminderEventSource): ReminderEventSource

    @Binds
    fun reReminders(coordinator: ReRemindCoordinator): InvitationReReminders

    @Binds
    fun reReminderAlarms(alarms: AndroidInvitationReReminderAlarms): InvitationReReminderAlarms

    @Binds
    fun beat(heartbeat: ReminderHeartbeat): ReminderBeat

    @Binds
    fun settings(settings: RepositoryReminderSettings): ReminderSettingsSource

    @Binds
    fun shown(shown: PreferencesShownReminders): ShownReminders

    @Binds
    fun snoozed(snoozed: PreferencesSnoozedReminders): SnoozedReminders
}

@Module
@InstallIn(SingletonComponent::class)
object ReminderModule {
    @Provides
    fun systemZone(): SystemZone = SystemZone { ZoneId.systemDefault() }
}
