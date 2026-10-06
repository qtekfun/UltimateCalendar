// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchQueryTest {
    @Test
    fun `a query is split into folded words`() {
        assertEquals(listOf("reunion", "sala"), SearchQuery.of("  REUNIÓN \t sala\n").words)
    }

    @Test
    fun `repeated words count once`() {
        assertEquals(listOf("cafe"), SearchQuery.of("café CAFE cafe").words)
    }

    @Test
    fun `nothing or only spaces is a blank query`() {
        assertTrue(SearchQuery.of("").isBlank)
        assertTrue(SearchQuery.of(" \n\t ").isBlank)
        assertFalse(SearchQuery.of("a").isBlank)
    }

    @Test
    fun `only the first words are kept`() {
        val words = SearchQuery.of((1..20).joinToString(" ") { "w$it" }).words

        assertEquals(SearchQuery.MAX_WORDS, words.size)
        assertEquals("w1", words.first())
    }

    @Test
    fun `the text is the folded words joined`() {
        assertEquals("reunion sala", SearchQuery.of("Reunión   Sala").text)
    }
}
