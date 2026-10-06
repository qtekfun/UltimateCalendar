// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

/**
 * What the user typed, as the words an event must all contain, in any order and in any field.
 * Words are folded (see [SearchText]); repeated words and anything past [MAX_WORDS] are dropped
 * so a pasted paragraph cannot make a search slow.
 */
data class SearchQuery(val words: List<String>) {
    val isBlank: Boolean get() = words.isEmpty()

    /** The query as its words joined, the form kept in the list of recent searches. */
    val text: String get() = words.joinToString(" ")

    companion object {
        const val MAX_WORDS = 6
        private val spaces = Regex("\\s+")

        fun of(raw: String): SearchQuery = SearchQuery(
            SearchText.fold(raw)
                .split(spaces)
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_WORDS)
        )
    }
}
