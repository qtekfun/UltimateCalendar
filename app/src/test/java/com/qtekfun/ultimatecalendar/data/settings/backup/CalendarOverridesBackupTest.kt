// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings.backup

import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.local.entity.PendingCalendarOverrideEntity
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.settings.AppSettings
import com.qtekfun.ultimatecalendar.data.settings.FakePreferences
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.data.settings.ThemeMode
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarOverridesBackupTest {
    private val google = CalendarAccount("ana@gmail.com", "com.google")
    private val davx5 = CalendarAccount("ana@cloud.example", "bitfire.at.davdroid")
    private lateinit var database: UltimateCalendarDatabase

    @BeforeEach
    fun open() {
        database = inMemoryDatabase()
    }

    @AfterEach
    fun close() = database.close()

    private fun calendar(id: Long, account: CalendarAccount, name: String) =
        CalendarInfo(CalendarId(id), account, name, 0xFF0000FF.toInt(), CalendarAccess.OWNER)

    private fun backupOf(vararg calendars: CalendarInfo) = CalendarOverridesBackup(
        FakeCalendarSource(calendars.toList()),
        database.calendarSettingsDao(),
        database.pendingCalendarOverrideDao(),
        Dispatchers.Unconfined
    )

    @Test
    fun `collect names each calendar by account and source name, not by id`() = runTest {
        val dao = database.calendarSettingsDao()
        dao.save(CalendarSettingsEntity(7, "Work", 0xFF112233.toInt(), false))
        val overrides = backupOf(calendar(7, google, "ana@gmail.com")).collect()

        assertEquals(
            listOf(
                BackupCalendar(
                    "com.google",
                    "ana@gmail.com",
                    "ana@gmail.com",
                    "Work",
                    0xFF112233.toInt(),
                    false
                )
            ),
            overrides
        )
    }

    @Test
    fun `collect skips calendars that are gone and rows that change nothing`() = runTest {
        val dao = database.calendarSettingsDao()
        dao.save(CalendarSettingsEntity(1, null, null, null))
        dao.save(CalendarSettingsEntity(99, "Gone", null, true))
        dao.save(CalendarSettingsEntity(2, null, null, true))

        val overrides = backupOf(calendar(1, google, "A"), calendar(2, google, "B")).collect()

        assertEquals(listOf("B"), overrides.map { it.name })
    }

    @Test
    fun `apply finds the calendar on the new phone under a different id`() = runTest {
        val entry = BackupCalendar("com.google", "ana@gmail.com", "Family", "Casa", 5, true)
        val applied = backupOf(calendar(42, google, "Family")).apply(listOf(entry))

        assertEquals(OverridesApplied(), applied)
        assertEquals(
            CalendarSettingsEntity(42, "Casa", 5, true),
            database.calendarSettingsDao().find(42)
        )
    }

    @Test
    fun `apply counts calendars that are not here and leaves the rest untouched`() = runTest {
        val onPhone = backupOf(calendar(1, google, "Family"), calendar(2, davx5, "Family"))
        val entries = listOf(
            BackupCalendar("com.google", "ana@gmail.com", "Family", "Casa", null, null),
            BackupCalendar("com.google", "ana@gmail.com", "Other", "x", null, null),
            BackupCalendar("bitfire.at.davdroid", "someone@else", "Family", "y", null, null)
        )

        assertEquals(OverridesApplied(missing = 2), onPhone.apply(entries))
        assertEquals(listOf(1L), database.calendarSettingsDao().all().map { it.calendarId })
    }

    @Test
    fun `apply does not guess between two calendars with the same account and name`() = runTest {
        val twins = backupOf(calendar(1, google, "Same"), calendar(2, google, "Same"))
        val entry = BackupCalendar("com.google", "ana@gmail.com", "Same", "x", null, null)

        assertEquals(OverridesApplied(missing = 1), twins.apply(listOf(entry)))
        assertTrue(database.calendarSettingsDao().all().isEmpty())
    }

    private val caldav = CalendarAccount("ana@cloud.example.com", CalendarAccount.CALDAV_TYPE)

    @Test
    fun `apply keeps the overrides of the signed-in CalDAV account's missing calendars waiting`() =
        runTest {
            val entries = listOf(
                BackupCalendar(caldav.type, caldav.name, "Work", "Job", 5, false),
                BackupCalendar(caldav.type, "bob@other.example", "Work", "x", null, null),
                BackupCalendar("com.google", caldav.name, "Work", "y", null, null)
            )

            val applied = backupOf().apply(entries, calDavAccount = caldav.name)

            // Only the signed-in CalDAV account's calendar can still appear; the rest are missing.
            assertEquals(OverridesApplied(missing = 2, waiting = 1), applied)
            assertEquals(
                listOf(PendingCalendarOverrideEntity(caldav.name, "Work", "Job", 5, false)),
                database.pendingCalendarOverrideDao().all()
            )
            assertTrue(database.calendarSettingsDao().all().isEmpty())
        }

    @Test
    fun `apply without a signed-in CalDAV account counts its calendars as missing`() = runTest {
        val entry = BackupCalendar(caldav.type, caldav.name, "Work", "Job", 5, false)

        assertEquals(OverridesApplied(missing = 1), backupOf().apply(listOf(entry), null))
        assertTrue(database.pendingCalendarOverrideDao().all().isEmpty())
    }

    @Test
    fun `apply uses a CalDAV calendar that is already there and does not park it`() = runTest {
        val entry = BackupCalendar(caldav.type, caldav.name, "Work", "Job", 5, false)

        val applied = backupOf(calendar(9, caldav, "Work")).apply(listOf(entry), caldav.name)

        assertEquals(OverridesApplied(), applied)
        assertEquals(
            CalendarSettingsEntity(9, "Job", 5, false),
            database.calendarSettingsDao().find(9)
        )
        assertTrue(database.pendingCalendarOverrideDao().all().isEmpty())
    }

    @Test
    fun `apply does not park an ambiguous CalDAV name`() = runTest {
        val entry = BackupCalendar(caldav.type, caldav.name, "Work", "Job", null, null)
        val twins = backupOf(calendar(1, caldav, "Work"), calendar(2, caldav, "Work"))

        assertEquals(OverridesApplied(missing = 1), twins.apply(listOf(entry), caldav.name))
        assertTrue(database.pendingCalendarOverrideDao().all().isEmpty())
    }

    @Test
    fun `overrides travel inside the encrypted backup and an old backup still opens`() {
        val passphrase = "correct horse".toCharArray()
        val settings = SettingsRepository(FakePreferences(), FakePreferences(), FakePreferences())
        val entry = BackupCalendar("com.google", "ana@gmail.com", "Family", "Casa", 5, false)

        val file = SettingsBackup(settings).export(passphrase, listOf(entry))
        val restored = SettingsBackup(settings).restore(file, passphrase)
        assertEquals(RestoreResult.Restored(listOf(entry)), restored)

        // A version 1 backup has no calendar list at all.
        val old = BackupCrypto.seal(
            """{"version":1,"settings":{"theme":"DARK"}}""".toByteArray(),
            passphrase,
            "UltimateCalendar:backup:1".toByteArray()
        )
        val oldFile = Json.encodeToString(BackupFile.serializer(), BackupFile(sealed = old))
        assertEquals(
            RestoreResult.Restored(),
            SettingsBackup(settings).restore(oldFile, passphrase)
        )
        assertEquals(
            AppSettings().copy(theme = com.qtekfun.ultimatecalendar.data.settings.ThemeMode.DARK),
            settings.current()
        )
    }
}
