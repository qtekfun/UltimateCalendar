// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.sync.engine.SessionSyncSource
import com.qtekfun.ultimatecalendar.sync.engine.SyncSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.random.Random

/** What the CalDAV sync engine needs (RF-12). Nothing starts a sync yet. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun syncSource(source: SessionSyncSource): SyncSource

    companion object {
        /** Spreads the retries of the queue so that devices do not retry in step. */
        @Provides
        fun random(): Random = Random.Default
    }
}
