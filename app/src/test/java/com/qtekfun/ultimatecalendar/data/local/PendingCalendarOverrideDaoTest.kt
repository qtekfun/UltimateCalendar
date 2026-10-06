// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import com.qtekfun.ultimatecalendar.data.local.dao.PendingCalendarOverrideDao
import com.qtekfun.ultimatecalendar.data.local.entity.PendingCalendarOverrideEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PendingCalendarOverrideDaoTest {
    private lateinit var database: UltimateCalendarDatabase
    private lateinit var dao: PendingCalendarOverrideDao

    @BeforeEach
    fun open() {
        database = inMemoryDatabase()
        dao = database.pendingCalendarOverrideDao()
    }

    @AfterEach
    fun close() = database.close()

    @Test
    fun `an account and a calendar name hold one row, the last one saved`() = runTest {
        dao.save(listOf(PendingCalendarOverrideEntity("ana@x", "Work", "Job", null, null)))
        dao.save(listOf(PendingCalendarOverrideEntity("ana@x", "Work", null, 5, false)))

        assertEquals(
            listOf(PendingCalendarOverrideEntity("ana@x", "Work", null, 5, false)),
            dao.forAccount("ana@x")
        )
    }

    @Test
    fun `rows are kept apart by account and deleted one at a time`() = runTest {
        dao.save(
            listOf(
                PendingCalendarOverrideEntity("ana@x", "Work", "A", null, null),
                PendingCalendarOverrideEntity("ana@x", "Gym", "B", null, null),
                PendingCalendarOverrideEntity("bob@y", "Work", "C", null, null)
            )
        )

        dao.delete("ana@x", "Work")

        assertEquals(listOf("Gym"), dao.forAccount("ana@x").map { it.calendarName })
        assertEquals(listOf("C"), dao.forAccount("bob@y").map { it.displayName })
        assertEquals(2, dao.all().size)
    }
}
