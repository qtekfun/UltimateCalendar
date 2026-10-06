// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecentSearchListTest {
    @Test
    fun `the newest search goes first`() {
        assertEquals(listOf("lunch", "budget"), RecentSearchList.add(listOf("budget"), "lunch"))
    }

    @Test
    fun `a repeated search moves up instead of being listed twice, ignoring case and accents`() {
        val list = listOf("cafe", "budget", "lunch")

        assertEquals(listOf("Café", "budget", "lunch"), RecentSearchList.add(list, "Café"))
        assertEquals(listOf("budget", "cafe", "lunch"), RecentSearchList.add(list, "  budget "))
    }

    @Test
    fun `extra spaces and line breaks are removed from what is kept`() {
        assertEquals(listOf("a b c"), RecentSearchList.add(emptyList(), " a \n b\t c "))
    }

    @Test
    fun `a blank search is not kept`() {
        assertEquals(listOf("a"), RecentSearchList.add(listOf("a"), "   "))
    }

    @Test
    fun `only the latest are kept`() {
        val full = (1..RecentSearchList.MAX).map { "q$it" }

        val list = RecentSearchList.add(full, "new")

        assertEquals(RecentSearchList.MAX, list.size)
        assertEquals("new", list.first())
        assertEquals("q${RecentSearchList.MAX - 1}", list.last())
    }

    @Test
    fun `a search can be removed, ignoring case and accents`() {
        assertEquals(
            listOf("budget"),
            RecentSearchList.remove(listOf("Café", "budget"), "cafe")
        )
        assertEquals(listOf("a"), RecentSearchList.remove(listOf("a"), "b"))
    }
}
