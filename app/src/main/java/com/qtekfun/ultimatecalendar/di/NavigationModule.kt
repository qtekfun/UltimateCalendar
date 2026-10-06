// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.data.invitations.InvitationInbox
import com.qtekfun.ultimatecalendar.domain.navigation.FirstDayOfWeekSource
import com.qtekfun.ultimatecalendar.domain.navigation.PendingInvitations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** What the app shell reads that other tasks will provide properly. */
@Module
@InstallIn(SingletonComponent::class)
object NavigationModule {
    @Provides
    fun firstDayOfWeek(): FirstDayOfWeekSource =
        FirstDayOfWeekSource { WeekFields.of(Locale.getDefault()).firstDayOfWeek }

    /** The badge of the tray counts what the invitation tray lists (RF-06). */
    @Provides
    fun pendingInvitations(inbox: InvitationInbox): PendingInvitations = PendingInvitations {
        inbox.pending().map { it.size }.onStart { emit(0) }.distinctUntilChanged()
    }
}
