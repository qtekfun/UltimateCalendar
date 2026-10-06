// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.data.invitations.InvitationInbox
import com.qtekfun.ultimatecalendar.data.settings.RepositoryNavigationSettings
import com.qtekfun.ultimatecalendar.domain.navigation.FirstDayOfWeekSource
import com.qtekfun.ultimatecalendar.domain.navigation.InitialViewSource
import com.qtekfun.ultimatecalendar.domain.navigation.PendingInvitations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** What the app shell reads: the settings that steer it and the invitation count. */
@Module
@InstallIn(SingletonComponent::class)
object NavigationModule {
    /** The first day of the week of Settings (RF-10), for the views and the widgets. */
    @Provides
    fun firstDayOfWeek(settings: RepositoryNavigationSettings): FirstDayOfWeekSource = settings

    /** The view the app opens on, from Settings (RF-10). */
    @Provides
    fun initialView(settings: RepositoryNavigationSettings): InitialViewSource = settings

    /** The badge of the tray counts what the invitation tray lists (RF-06). */
    @Provides
    fun pendingInvitations(inbox: InvitationInbox): PendingInvitations = PendingInvitations {
        inbox.pending().map { it.size }.onStart { emit(0) }.distinctUntilChanged()
    }
}
