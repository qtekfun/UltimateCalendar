// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.domain.navigation.FirstDayOfWeekSource
import com.qtekfun.ultimatecalendar.domain.navigation.PendingInvitations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.flow.flowOf

/** What the app shell reads that other tasks will provide properly. */
@Module
@InstallIn(SingletonComponent::class)
object NavigationModule {
    @Provides
    fun firstDayOfWeek(): FirstDayOfWeekSource =
        FirstDayOfWeekSource { WeekFields.of(Locale.getDefault()).firstDayOfWeek }

    /** No invitations are detected yet: T21 replaces this with the `InvitationDetector`. */
    @Provides
    fun pendingInvitations(): PendingInvitations = PendingInvitations { flowOf(0) }
}
