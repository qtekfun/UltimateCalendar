// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.data.settings.RepositoryReminderSettings
import com.qtekfun.ultimatecalendar.domain.reminders.ReminderEventSource
import com.qtekfun.ultimatecalendar.notify.PreferencesShownReminders
import com.qtekfun.ultimatecalendar.notify.ReminderBeat
import com.qtekfun.ultimatecalendar.notify.ReminderHeartbeat
import com.qtekfun.ultimatecalendar.notify.ReminderSettingsSource
import com.qtekfun.ultimatecalendar.notify.ShownReminders
import com.qtekfun.ultimatecalendar.notify.SystemZone
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf

@Module
@InstallIn(SingletonComponent::class)
interface ReminderBindingsModule {
    @Binds
    fun beat(heartbeat: ReminderHeartbeat): ReminderBeat

    @Binds
    fun settings(settings: RepositoryReminderSettings): ReminderSettingsSource

    @Binds
    fun shown(shown: PreferencesShownReminders): ShownReminders
}

@Module
@InstallIn(SingletonComponent::class)
object ReminderModule {
    @Provides
    fun systemZone(): SystemZone = SystemZone { ZoneId.systemDefault() }

    /** No calendar source yet (T04, T05): nothing reminds until one is bound here. */
    @Provides
    fun eventSource(): ReminderEventSource = ReminderEventSource { _, _ -> flowOf(emptyList()) }
}
