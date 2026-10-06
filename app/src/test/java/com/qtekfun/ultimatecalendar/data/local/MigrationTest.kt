// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.local.entity.NotifiedInvitationEntity
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Builds a real version 1 database from the schema Room exported for it, as a phone that already
 * has the app would hold, and opens it with the current code: Room runs the migrations and checks
 * that the result is exactly the exported schema of the latest version.
 */
class MigrationTest {
    private val schemas = File("schemas/${UltimateCalendarDatabase::class.qualifiedName}")

    private fun createVersion1(file: File) {
        val json = File(schemas, "1.json").readText()
        val tables = Regex("\"tableName\": \"(\\w+)\",\\s*\"createSql\": \"(.*)\",").findAll(json)
            .map { it.groupValues[2].replace("\${TABLE_NAME}", it.groupValues[1]) }
        val setup = Regex("\"((?:CREATE|INSERT)[^\"]*room_master_table[^\"]*)\"")
            .findAll(json).map { it.groupValues[1] }
        val connection = BundledSQLiteDriver().open(file.path)
        try {
            (tables + setup + sequenceOf("PRAGMA user_version = 1")).forEach {
                connection.execSQL(it)
            }
            connection.execSQL(
                "INSERT INTO calendar_settings (calendarId, displayName, color, visible) " +
                    "VALUES (7, 'Work', 255, 0)"
            )
        } finally {
            connection.close()
        }
    }

    private fun open(file: File): UltimateCalendarDatabase {
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getDatabasePath(file.name) } returns file
        return Room.databaseBuilder(context, UltimateCalendarDatabase::class.java, file.name)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*UltimateCalendarDatabase.MIGRATIONS)
            .build()
    }

    @Test
    fun `version 1 data survives and the new table works after migrating to 2`(@TempDir dir: File) =
        runTest {
            val file = File(dir, "calendar.db")
            createVersion1(file)

            val database = open(file)
            try {
                val settings = database.calendarSettingsDao().find(7)
                val invitations = database.notifiedInvitationDao()
                assertEquals(emptyList<NotifiedInvitationEntity>(), invitations.all())
                val row = NotifiedInvitationEntity(7, 9, "Lunch", false, 1, 2, "UTC", null, null)
                invitations.replaceAll(listOf(row))

                assertEquals(CalendarSettingsEntity(7, "Work", 255, false), settings)
                assertEquals(listOf(row), invitations.all())
            } finally {
                database.close()
            }
        }
}
