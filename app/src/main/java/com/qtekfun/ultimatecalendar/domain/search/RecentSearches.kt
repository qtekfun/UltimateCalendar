// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import kotlinx.coroutines.flow.Flow

/**
 * The searches the user ran lately, newest first, kept on the phone only. What was typed is
 * private: it is never logged and never leaves the device (nor goes into a backup).
 */
interface RecentSearches {
    val searches: Flow<List<String>>

    suspend fun remember(query: String)

    suspend fun forget(query: String)

    suspend fun clear()
}

/** The rules of the list of recent searches, apart from where it is stored. */
object RecentSearchList {
    const val MAX = 8

    /** [list] with [query] first: repeated searches (no case, no accents) move up, the oldest drop. */
    fun add(list: List<String>, query: String): List<String> {
        val clean = clean(query)
        return if (clean.isEmpty()) {
            list
        } else {
            (listOf(clean) + list.filterNot { same(it, clean) }).take(MAX)
        }
    }

    fun remove(list: List<String>, query: String): List<String> = list.filterNot { same(it, query) }

    /** The query as the user typed it, without line breaks and extra spaces. */
    fun clean(query: String): String = query.trim().replace(Regex("\\s+"), " ")

    private fun same(a: String, b: String) = SearchQuery.of(a) == SearchQuery.of(b)
}
