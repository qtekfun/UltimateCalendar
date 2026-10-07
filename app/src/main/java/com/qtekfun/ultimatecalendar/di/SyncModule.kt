// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import android.content.Context
import android.content.SharedPreferences
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionRefresher
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionsRefresh
import com.qtekfun.ultimatecalendar.sync.CalDavSync
import com.qtekfun.ultimatecalendar.sync.OwnAccountSync
import com.qtekfun.ultimatecalendar.sync.engine.LastSyncStore
import com.qtekfun.ultimatecalendar.sync.engine.PreferencesLastSyncStore
import com.qtekfun.ultimatecalendar.sync.engine.SessionSyncSource
import com.qtekfun.ultimatecalendar.sync.engine.SyncSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import kotlin.random.Random

/** What the CalDAV sync engine needs (RF-12). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun syncSource(source: SessionSyncSource): SyncSource

    @Binds
    abstract fun ownAccountSync(sync: CalDavSync): OwnAccountSync

    @Binds
    abstract fun subscriptionsRefresh(refresher: SubscriptionRefresher): SubscriptionsRefresh

    @Binds
    abstract fun lastSync(store: PreferencesLastSyncStore): LastSyncStore

    companion object {
        /** Spreads the retries of the queue so that devices do not retry in step. */
        @Provides
        fun random(): Random = Random.Default

        @Provides
        @Named(PreferencesLastSyncStore.FILE)
        fun lastSyncPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences(PreferencesLastSyncStore.FILE, Context.MODE_PRIVATE)
    }
}
