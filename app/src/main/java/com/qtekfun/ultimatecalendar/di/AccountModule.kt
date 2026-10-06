// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.data.source.caldav.AccountRemovedListener
import com.qtekfun.ultimatecalendar.notify.RemindersAfterSignOut
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** What reacts when the CalDAV account is signed out (T37). */
@Module
@InstallIn(SingletonComponent::class)
interface AccountModule {
    @Binds
    fun accountRemoved(listener: RemindersAfterSignOut): AccountRemovedListener
}
