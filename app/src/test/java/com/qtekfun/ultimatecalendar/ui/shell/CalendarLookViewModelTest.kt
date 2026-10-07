// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.shell

import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.CalendarSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarLookViewModelTest {
    private val work = CalendarInfo(
        CalendarId(1),
        CalendarAccount("me@example.com", "com.google"),
        "Work",
        0xFF0B63CE.toInt(),
        CalendarAccess.OWNER
    )
    private lateinit var database: UltimateCalendarDatabase
    private lateinit var source: FakeCalendarSource
    private lateinit var repository: CalendarRepository
    private lateinit var model: CalendarLookViewModel

    @BeforeEach
    fun open() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = inMemoryDatabase()
        source = FakeCalendarSource(listOf(work))
        repository =
            CalendarRepository(source, database.calendarSettingsDao(), Dispatchers.Unconfined)
        model = CalendarLookViewModel(repository)
    }

    @AfterEach
    fun close() {
        model.viewModelScope.cancel()
        database.close()
    }

    @Test
    fun `a name and a color are stored on this phone and the source is not touched`() = runTest {
        model.save(work, "Job", 0xFF112233.toInt()).join()

        assertStored(CalendarSettings("Job", 0xFF112233.toInt()))
        assertEquals("Work", source.calendars().value.single().displayName)
    }

    @Test
    fun `leaving what was shown keeps the override and a reset forgets it`() = runTest {
        repository.saveSettings(work.id, CalendarSettings("Job", 1))

        model.save(work.copy(displayName = "Job", color = 1), "Job", 1).join()
        assertStored(CalendarSettings("Job", 1))

        model.save(work.copy(displayName = "Job", color = 1), "", null).join()
        assertStored(CalendarSettings())
    }

    /** The write goes through Room's own threads: wait (really) until it shows. */
    private suspend fun assertStored(expected: CalendarSettings) =
        withContext(Dispatchers.Default) {
            val found = withTimeoutOrNull(WAIT_MS) {
                while (repository.settings(work.id) != expected) delay(POLL_MS)
                expected
            }
            assertEquals(expected, found ?: repository.settings(work.id))
        }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
