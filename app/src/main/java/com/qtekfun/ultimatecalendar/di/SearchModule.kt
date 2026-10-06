// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import android.content.Context
import android.content.SharedPreferences
import com.qtekfun.ultimatecalendar.data.search.PreferencesRecentSearches
import com.qtekfun.ultimatecalendar.domain.search.RecentSearches
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/** Where the list of recent searches lives. */
@Module
@InstallIn(SingletonComponent::class)
interface SearchModule {
    @Binds
    @Singleton
    fun recentSearches(searches: PreferencesRecentSearches): RecentSearches

    companion object {
        @Provides
        @Named(PreferencesRecentSearches.FILE)
        fun recentSearchesPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences(PreferencesRecentSearches.FILE, Context.MODE_PRIVATE)
    }
}
