// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.search

import android.content.SharedPreferences
import androidx.core.content.edit
import com.qtekfun.ultimatecalendar.domain.search.RecentSearchList
import com.qtekfun.ultimatecalendar.domain.search.RecentSearches
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [RecentSearches] in their own SharedPreferences file, apart from the settings so they are never
 * part of a backup. One entry per line (a search has none: [RecentSearchList.clean] removes them).
 * What is stored is never logged.
 */
@Singleton
class PreferencesRecentSearches @Inject constructor(
    @Named(FILE) private val preferences: SharedPreferences
) : RecentSearches {
    override val searches: Flow<List<String>> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(read())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override suspend fun remember(query: String) = change { RecentSearchList.add(it, query) }

    override suspend fun forget(query: String) = change { RecentSearchList.remove(it, query) }

    override suspend fun clear() = change { emptyList() }

    @Synchronized
    private fun change(transform: (List<String>) -> List<String>) {
        preferences.edit { putString(KEY, transform(read()).joinToString(SEPARATOR)) }
    }

    private fun read(): List<String> =
        preferences.getString(KEY, null)?.split(SEPARATOR).orEmpty().filter { it.isNotEmpty() }

    companion object {
        const val FILE = "recent_searches"
        private const val KEY = "searches"
        private const val SEPARATOR = "\n"
    }
}
