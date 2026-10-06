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
import com.qtekfun.ultimatecalendar.data.local.entity.ReRemindEntity
import com.qtekfun.ultimatecalendar.data.local.entity.SubscriptionEntity
import com.qtekfun.ultimatecalendar.data.local.entity.SubscriptionEventEntity
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
        val indices = json.split("\"tableName\": \"").drop(1).flatMap { block ->
            val name = block.substringBefore('"')
            Regex("\"createSql\": \"(CREATE [^\"]*INDEX[^\"]*)\"").findAll(block)
                .map { it.groupValues[1].replace("\${TABLE_NAME}", name) }.toList()
        }
        val setup = Regex("\"((?:CREATE|INSERT)[^\"]*room_master_table[^\"]*)\"")
            .findAll(json).map { it.groupValues[1] }
        val connection = BundledSQLiteDriver().open(file.path)
        try {
            (tables + indices + setup + sequenceOf("PRAGMA user_version = $version")).forEach {
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
            assertReRemindTableWorks(database)
            assertSubscriptionTablesWork(database)
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
            assertReRemindTableWorks(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun `an account of version 4 keeps its row, without addresses or scheduling, in version 5`(
        @TempDir dir: File
    ) = runTest {
        val file = File(dir, "calendar.db")
        createVersion(4, file)
        val connection = BundledSQLiteDriver().open(file.path)
        try {
            connection.execSQL(
                "INSERT INTO dav_account (id, serverUrl, loginName, calendarHome) " +
                    "VALUES (5, 'https://cloud.example.com/', 'ana', '/dav/calendars/ana/')"
            )
        } finally {
            connection.close()
        }

        val database = open(file)
        try {
            val account = requireNotNull(database.davAccountDao().get(5))
            assertEquals("/dav/calendars/ana/", account.calendarHome)
            assertEquals("", account.userAddresses)
            assertEquals(false, account.scheduling)
            database.davAccountDao().update(
                account.copy(userAddresses = "ana@example.com", scheduling = true)
            )
            assertEquals("ana@example.com", database.davAccountDao().get(5)?.userAddresses)
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 3 data survives and the re-reminders table is created by the migration to 4`(
        @TempDir dir: File
    ) = runTest {
        val file = File(dir, "calendar.db")
        createVersion(3, file)

        val database = open(file)
        try {
            assertEquals(
                listOf(NotifiedInvitationEntity(7, 9, "Lunch", false, 1, 2, "UTC", null, null)),
                database.notifiedInvitationDao().all()
            )
            assertCalDavTablesWork(database)
            assertReRemindTableWorks(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 5 data survives and the subscription tables are created by the migration to 6`(
        @TempDir dir: File
    ) = runTest {
        val file = File(dir, "calendar.db")
        createVersion(5, file)

        val database = open(file)
        try {
            assertEquals(
                CalendarSettingsEntity(7, "Work", 255, false),
                database.calendarSettingsDao().find(7)
            )
            assertEquals(emptyList<SubscriptionEntity>(), database.subscriptionDao().all())
            assertSubscriptionTablesWork(database)
        } finally {
            database.close()
        }
    }

    /** Subscriptions keep their events apart by UID and take them along when removed. */
    private suspend fun assertSubscriptionTablesWork(database: UltimateCalendarDatabase) {
        val subscription = database.subscriptionDao().insert(
            SubscriptionEntity(
                name = "Holidays",
                color = 1,
                refreshHours = 12,
                host = "example.com",
                urlKey = "key",
                urlSecret = "sealed"
            )
        )
        val event = database.subscriptionEventDao().insert(
            SubscriptionEventEntity(
                subscriptionId = subscription,
                uid = "a@x",
                title = "New Year",
                start = 1,
                end = 2,
                windowStart = 1,
                windowEnd = 2
            )
        )
        assertEquals("New Year", database.subscriptionEventDao().get(event)?.title)
        assertEquals(1, database.subscriptionEventDao().inWindow(listOf(subscription), 0, 10).size)

        database.subscriptionDao().delete(subscription)

        assertNull(database.subscriptionEventDao().get(event))
    }

    private suspend fun assertReRemindTableWorks(database: UltimateCalendarDatabase) {
        val dao = database.reRemindDao()
        assertEquals(emptyList<ReRemindEntity>(), dao.all())
        val shown = ReRemindEntity(7, 9, "DAY_BEFORE", 1, 100, true)
        val waiting = ReRemindEntity(7, 9, "HOUR_BEFORE", 1, 200, false)
        dao.apply(listOf(shown, waiting), emptyList())
        dao.apply(listOf(waiting.copy(at = 300)), listOf(shown))
        assertEquals(listOf(waiting.copy(at = 300)), dao.all())
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
