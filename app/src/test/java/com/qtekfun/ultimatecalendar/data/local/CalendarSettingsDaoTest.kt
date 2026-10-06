// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.local.dao.CalendarSettingsDao
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarSettingsDaoTest {
    private lateinit var database: UltimateCalendarDatabase
    private lateinit var dao: CalendarSettingsDao

    @BeforeEach
    fun open() {
        database = inMemoryDatabase()
        dao = database.calendarSettingsDao()
    }

    @AfterEach
    fun close() = database.close()

    @Test
    fun `saving twice for a calendar keeps the last settings only`() = runTest {
        dao.save(CalendarSettingsEntity(1, "Work", null, false))
        dao.save(CalendarSettingsEntity(1, null, 0xFF112233.toInt(), null))

        dao.observeAll().test {
            assertEquals(
                listOf(CalendarSettingsEntity(1, null, 0xFF112233.toInt(), null)),
                awaitItem()
            )
        }
    }

    @Test
    fun `clearing forgets the overrides of one calendar`() = runTest {
        dao.save(CalendarSettingsEntity(1, "A", null, null))
        dao.save(CalendarSettingsEntity(2, "B", null, null))

        dao.clear(1)

        assertNull(dao.find(1))
        assertEquals("B", dao.find(2)?.displayName)
    }
}
