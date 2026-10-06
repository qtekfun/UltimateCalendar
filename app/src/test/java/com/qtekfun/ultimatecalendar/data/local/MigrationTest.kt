// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.local

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavAccountEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavCalendarEntity
import com.qtekfun.ultimatecalendar.data.local.entity.DavEventEntity
import com.qtekfun.ultimatecalendar.data.local.entity.NotifiedInvitationEntity
import com.qtekfun.ultimatecalendar.data.local.entity.PendingOperationEntity
import com.qtekfun.ultimatecalendar.data.local.model.OperationType
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Builds a real version 1 database from the schema Room exported for it, as a phone that already
 * has the app would hold, and opens it with the current code: Room runs the migrations and checks
 * that the result is exactly the exported schema of the latest version.
 */
class MigrationTest {
    private val schemas = File("schemas/${UltimateCalendarDatabase::class.qualifiedName}")

    private fun createVersion(version: Int, file: File) {
        val json = File(schemas, "$version.json").readText()
        val tables = Regex("\"tableName\": \"(\\w+)\",\\s*\"createSql\": \"(.*)\",").findAll(json)
            .map { it.groupValues[2].replace("\${TABLE_NAME}", it.groupValues[1]) }
        val setup = Regex("\"((?:CREATE|INSERT)[^\"]*room_master_table[^\"]*)\"")
            .findAll(json).map { it.groupValues[1] }
        val connection = BundledSQLiteDriver().open(file.path)
        try {
            (tables + setup + sequenceOf("PRAGMA user_version = $version")).forEach {
                connection.execSQL(it)
            }
            connection.execSQL(
                "INSERT INTO calendar_settings (calendarId, displayName, color, visible) " +
                    "VALUES (7, 'Work', 255, 0)"
            )
            if (version >= 2) {
                connection.execSQL(
                    "INSERT INTO notified_invitations VALUES (7, 9, 'Lunch', 0, 1, 2, 'UTC', NULL, NULL)"
                )
            }
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
    fun `version 1 data survives and the new tables work after migrating to the latest`(
        @TempDir dir: File
    ) = runTest {
        val file = File(dir, "calendar.db")
        createVersion(1, file)

        val database = open(file)
        try {
            val settings = database.calendarSettingsDao().find(7)
            val invitations = database.notifiedInvitationDao()
            assertEquals(emptyList<NotifiedInvitationEntity>(), invitations.all())
            val row = NotifiedInvitationEntity(7, 9, "Lunch", false, 1, 2, "UTC", null, null)
            invitations.replaceAll(listOf(row))

            assertEquals(CalendarSettingsEntity(7, "Work", 255, false), settings)
            assertEquals(listOf(row), invitations.all())
            assertCalDavTablesWork(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 2 data survives and the CalDAV tables are created by the migration to 3`(
        @TempDir dir: File
    ) = runTest {
        val file = File(dir, "calendar.db")
        createVersion(2, file)

        val database = open(file)
        try {
            assertEquals(
                CalendarSettingsEntity(7, "Work", 255, false),
                database.calendarSettingsDao().find(7)
            )
            assertEquals(
                listOf(NotifiedInvitationEntity(7, 9, "Lunch", false, 1, 2, "UTC", null, null)),
                database.notifiedInvitationDao().all()
            )
            assertCalDavTablesWork(database)
        } finally {
            database.close()
        }
    }

    /** The new tables accept rows, keep them apart by account and cascade on delete. */
    private suspend fun assertCalDavTablesWork(database: UltimateCalendarDatabase) {
        val account = database.davAccountDao()
            .insert(DavAccountEntity(serverUrl = "https://cloud.example.com/", loginName = "ana"))
        val calendar = database.davCalendarDao()
            .insert(DavCalendarEntity(accountId = account, href = "/cal/work/", name = "Work"))
        val event = database.davEventDao().insert(
            DavEventEntity(
                accountId = account,
                calendarId = calendar,
                href = "/cal/work/1.ics",
                uid = "1",
                title = "Standup",
                start = 1,
                end = 2,
                windowStart = 1,
                windowEnd = 2
            )
        )
        database.pendingOperationDao().insert(
            PendingOperationEntity(
                accountId = account,
                type = OperationType.CREATE,
                eventId = event,
                createdAt = 1
            )
        )
        assertEquals("Standup", database.davEventDao().get(event)?.title)
        assertEquals(1, database.pendingOperationDao().all(account).size)

        database.davAccountDao().delete(account)

        assertNull(database.davEventDao().get(event))
        assertEquals(
            emptyList<PendingOperationEntity>(),
            database.pendingOperationDao().all(account)
        )
    }
}
