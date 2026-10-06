// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** Time, injected so tests can fix it. */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    fun clock(): Clock = Clock.systemUTC()
}
