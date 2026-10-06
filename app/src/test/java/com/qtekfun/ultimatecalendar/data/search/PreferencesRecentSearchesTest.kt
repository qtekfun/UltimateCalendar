// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.search

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.settings.FakePreferences
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreferencesRecentSearchesTest {
    private val preferences = FakePreferences()
    private val recent = PreferencesRecentSearches(preferences)

    @Test
    fun `nothing is remembered at first`() = runTest {
        recent.searches.test { assertEquals(emptyList<String>(), awaitItem()) }
    }

    @Test
    fun `searches are remembered newest first and every change is announced`() = runTest {
        recent.searches.test {
            assertEquals(emptyList<String>(), awaitItem())

            recent.remember("budget")
            assertEquals(listOf("budget"), awaitItem())

            recent.remember("Lunch  with Ana")
            assertEquals(listOf("Lunch with Ana", "budget"), awaitItem())

            recent.remember("BUDGET")
            assertEquals(listOf("BUDGET", "Lunch with Ana"), awaitItem())
        }
    }

    @Test
    fun `they survive a new instance over the same preferences`() = runTest {
        recent.remember("budget")

        PreferencesRecentSearches(preferences).searches.test {
            assertEquals(listOf("budget"), awaitItem())
        }
    }

    @Test
    fun `one search can be forgotten and all can be cleared`() = runTest {
        recent.remember("a")
        recent.remember("b")
        recent.remember("c")

        recent.forget("B")
        recent.searches.test { assertEquals(listOf("c", "a"), awaitItem()) }

        recent.clear()
        recent.searches.test { assertEquals(emptyList<String>(), awaitItem()) }
    }

    @Test
    fun `a blank search is not stored`() = runTest {
        recent.remember("   ")

        recent.searches.test { assertEquals(emptyList<String>(), awaitItem()) }
    }
}
