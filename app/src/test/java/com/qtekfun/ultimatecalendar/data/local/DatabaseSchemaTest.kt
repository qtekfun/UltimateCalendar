// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatabaseSchemaTest {
    private val schemas = File("schemas/${UltimateCalendarDatabase::class.qualifiedName}")

    @Test
    fun `every database version has its exported schema committed`() {
        (1..UltimateCalendarDatabase.VERSION).forEach { version ->
            assertTrue(File(schemas, "$version.json").isFile, "missing schema for version $version")
        }
    }

    @Test
    fun `the latest exported schema matches the database version`() {
        val latest = File(schemas, "${UltimateCalendarDatabase.VERSION}.json").readText()

        assertTrue(latest.contains("\"version\": ${UltimateCalendarDatabase.VERSION},"))
    }

    @Test
    fun `every version after the first is reached by a migration`() {
        val steps = UltimateCalendarDatabase.MIGRATIONS.map {
            it.startVersion to it.endVersion
        }.toSet()

        assertEquals((2..UltimateCalendarDatabase.VERSION).map { it - 1 to it }.toSet(), steps)
    }
}
