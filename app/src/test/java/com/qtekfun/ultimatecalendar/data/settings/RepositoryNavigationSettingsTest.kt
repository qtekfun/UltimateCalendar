// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.domain.navigation.CalendarView
import com.qtekfun.ultimatecalendar.domain.settings.FirstDayOfWeek
import com.qtekfun.ultimatecalendar.domain.settings.InitialView
import java.time.DayOfWeek
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RepositoryNavigationSettingsTest {
    private val repository =
        SettingsRepository(FakePreferences(), FakePreferences(), FakePreferences())
    private val navigation = RepositoryNavigationSettings(repository)

    @Test
    fun `the first day is the one picked in Settings`() = runTest {
        repository.update { it.copy(firstDayOfWeek = FirstDayOfWeek.SATURDAY) }

        assertEquals(DayOfWeek.SATURDAY, navigation.current())
    }

    @Test
    fun `a change of the first day reaches whoever follows it`() = runTest {
        repository.update { it.copy(firstDayOfWeek = FirstDayOfWeek.MONDAY) }

        navigation.changes().test {
            assertEquals(DayOfWeek.MONDAY, awaitItem())
            repository.update { it.copy(firstDayOfWeek = FirstDayOfWeek.SUNDAY) }
            assertEquals(DayOfWeek.SUNDAY, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the initial view is the one picked in Settings`() {
        InitialView.entries.forEach { choice ->
            repository.update { it.copy(initialView = choice) }

            assertEquals(CalendarView.valueOf(choice.name), navigation.initial())
        }
    }
}
