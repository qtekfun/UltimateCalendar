// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings.backup

import com.qtekfun.ultimatecalendar.data.settings.FakePreferences
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The optional CalDAV sign-in inside the sealed content of a backup (T37). */
class SettingsBackupSessionTest {
    private val json = Json { encodeDefaults = true }
    private val lenient = Json { ignoreUnknownKeys = true }
    private val passphrase = "correct horse".toCharArray()
    private val session =
        BackupSession("https://cloud.example.com/", "ana", "xk3-secret-app-password")

    private fun phone() =
        SettingsRepository(FakePreferences(), FakePreferences(), FakePreferences())

    private fun fileWith(content: String): String {
        val sealed = BackupCrypto.seal(
            content.toByteArray(),
            passphrase,
            "UltimateCalendar:backup:$BACKUP_FORMAT".toByteArray()
        )
        return json.encodeToString(BackupFile(sealed = sealed))
    }

    @Test
    fun `the session makes the round trip`() {
        val file = SettingsBackup(phone()).export(passphrase, session = session)

        val restored = SettingsBackup(phone()).restore(file, passphrase)

        assertEquals(RestoreResult.Restored(session = session), restored)
    }

    @Test
    fun `without the switch the file has no session`() {
        val file = SettingsBackup(phone()).export(passphrase)

        val restored = SettingsBackup(phone()).restore(file, passphrase)

        assertEquals(RestoreResult.Restored(), restored)
        assertNull((restored as RestoreResult.Restored).session)
    }

    @Test
    fun `the password is only ever inside the sealed content`() {
        val file = SettingsBackup(phone()).export(passphrase, session = session)

        assertFalse("xk3-secret-app-password" in file)
        assertFalse("cloud.example.com" in file)
        // And the sealed bytes do not contain it in the clear either.
        val sealed = json.decodeFromString<BackupFile>(file).sealed
        val raw = Base64.getDecoder().decode(sealed.data).toString(Charsets.ISO_8859_1)
        assertFalse("xk3-secret-app-password" in raw)
    }

    @Test
    fun `a wrong passphrase gives nothing away`() {
        val file = SettingsBackup(phone()).export(passphrase, session = session)

        val restored = SettingsBackup(phone()).restore(file, "wrong passphrase".toCharArray())

        assertEquals(RestoreResult.WrongPassphrase, restored)
    }

    @Test
    fun `the content version stays compatible`() {
        val file = SettingsBackup(phone()).export(passphrase, session = session)
        val plain = BackupCrypto.open(
            json.decodeFromString<BackupFile>(file).sealed,
            passphrase,
            "UltimateCalendar:backup:$BACKUP_FORMAT".toByteArray()
        )

        val content = json.decodeFromString<BackupContent>(plain!!.decodeToString())

        assertEquals(2, BACKUP_CONTENT_VERSION)
        assertEquals(BACKUP_CONTENT_VERSION, content.version)
        assertEquals(session, content.caldav)
    }

    @Test
    fun `a backup written before the session existed restores without one`() {
        val older = fileWith("{\"version\":2,\"settings\":{\"theme\":\"LIGHT\"},\"calendars\":[]}")

        val restored = SettingsBackup(phone()).restore(older, passphrase)

        assertEquals(RestoreResult.Restored(), restored)
    }

    @Test
    fun `an older app reading the content ignores the session`() {
        // What an app that does not know the field does: ignore unknown keys.
        val file = SettingsBackup(phone()).export(passphrase, session = session)
        val plain = BackupCrypto.open(
            json.decodeFromString<BackupFile>(file).sealed,
            passphrase,
            "UltimateCalendar:backup:$BACKUP_FORMAT".toByteArray()
        )!!.decodeToString()

        val olderApp = lenient.decodeFromString<OlderContent>(plain)

        assertTrue(olderApp.version <= BACKUP_CONTENT_VERSION)
    }

    @Test
    fun `a session with a missing field is not a session`() {
        val broken = fileWith(
            "{\"version\":2,\"settings\":{},\"caldav\":{\"serverUrl\":\"https://cloud.example.com/\"}}"
        )

        assertEquals(RestoreResult.Invalid, SettingsBackup(phone()).restore(broken, passphrase))
    }

    @kotlinx.serialization.Serializable
    private data class OlderContent(val version: Int, val settings: BackupSettings)
}
