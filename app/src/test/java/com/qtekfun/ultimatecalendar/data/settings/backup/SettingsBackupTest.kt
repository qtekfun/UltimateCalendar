// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings.backup

import com.qtekfun.ultimatecalendar.data.settings.AppSettings
import com.qtekfun.ultimatecalendar.data.settings.FakePreferences
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.data.settings.ThemeMode
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindOption
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.settings.FirstDayOfWeek
import com.qtekfun.ultimatecalendar.domain.settings.InitialView
import com.qtekfun.ultimatecalendar.domain.settings.InviteCheckInterval
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsBackupTest {
    private val json = Json { encodeDefaults = true }
    private val oldPhone =
        SettingsRepository(FakePreferences(), FakePreferences(), FakePreferences())
    private val newPhone =
        SettingsRepository(FakePreferences(), FakePreferences(), FakePreferences())
    private val passphrase = "correct horse".toCharArray()

    private val customized = AppSettings(
        theme = ThemeMode.DARK,
        amoled = true,
        dynamicColor = false,
        firstDayOfWeek = FirstDayOfWeek.MONDAY,
        initialView = InitialView.WEEK,
        defaultCalendar = CalendarId(5),
        defaultDurationMinutes = 45,
        defaultReminders = listOf(5, 60),
        defaultAllDayReminders = listOf(0, 1440),
        inviteCheck = InviteCheckInterval.EVERY_15,
        ownEmails = listOf("ana@example.com"),
        notifyChanges = true,
        notifyCancellations = true,
        reRemind = ReRemindOption.HOUR_BEFORE,
        missedWindowHours = 6,
        alarmClock = true,
        robustMode = true,
        allDayMinute = 8 * 60
    )

    private fun exported(): String {
        oldPhone.update { customized }
        return SettingsBackup(oldPhone).export(passphrase)
    }

    private fun restorer() = SettingsBackup(newPhone)

    /** A file as an app of [format] would write it, with [content] sealed inside. */
    private fun fileWith(
        content: String,
        format: Int = BACKUP_FORMAT,
        app: String = "UltimateCalendar"
    ): String {
        val sealed = BackupCrypto.seal(
            content.toByteArray(),
            passphrase,
            "UltimateCalendar:backup:$format".toByteArray()
        )
        return json.encodeToString(BackupFile(app, format, sealed))
    }

    private fun alter(backup: String, change: (BackupFile) -> BackupFile): String =
        json.encodeToString(change(json.decodeFromString<BackupFile>(backup)))

    @Test
    fun `settings travel to a new phone`() {
        val backup = exported()
        assertEquals(RestoreResult.Restored(), restorer().restore(backup, passphrase))
        // The default calendar is an id of the old phone: it is not carried.
        assertEquals(customized.copy(defaultCalendar = null), newPhone.current())
    }

    @Test
    fun `nothing readable is left in the file`() {
        val backup = exported()
        assertFalse("ana@example.com" in backup)
        assertFalse("DARK" in backup)
        assertTrue("UltimateCalendar" in backup)
    }

    @Test
    fun `every export is different`() {
        oldPhone.update { customized }
        val exporter = SettingsBackup(oldPhone)
        assertNotEquals(exporter.export(passphrase), exporter.export(passphrase))
    }

    @Test
    fun `a short passphrase is refused when exporting`() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackup(oldPhone).export("short".toCharArray())
        }
    }

    @Test
    fun `a wrong passphrase restores nothing`() {
        val backup = exported()
        assertEquals(
            RestoreResult.WrongPassphrase,
            restorer().restore(backup, "wrong passphrase".toCharArray())
        )
        assertEquals(AppSettings(), newPhone.current())
    }

    @Test
    fun `a tampered file restores nothing`() {
        val backup = exported()
        val flipped = alter(backup) { file ->
            val bytes = Base64.getDecoder().decode(file.sealed.data)
            bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte()
            file.copy(sealed = file.sealed.copy(data = Base64.getEncoder().encodeToString(bytes)))
        }
        assertEquals(RestoreResult.WrongPassphrase, restorer().restore(flipped, passphrase))
        val otherSalt = alter(backup) { it.copy(sealed = it.sealed.copy(salt = exportedSalt())) }
        assertEquals(RestoreResult.WrongPassphrase, restorer().restore(otherSalt, passphrase))
        val moreIterations =
            alter(backup) { it.copy(sealed = it.sealed.copy(iterations = 300_000)) }
        assertEquals(RestoreResult.WrongPassphrase, restorer().restore(moreIterations, passphrase))
        assertEquals(AppSettings(), newPhone.current())
    }

    private fun exportedSalt(): String = json.decodeFromString<BackupFile>(exported()).sealed.salt

    @Test
    fun `a file that is not a backup is invalid`() {
        val restorer = restorer()
        assertEquals(RestoreResult.Invalid, restorer.restore("", passphrase))
        assertEquals(RestoreResult.Invalid, restorer.restore("not json at all", passphrase))
        assertEquals(RestoreResult.Invalid, restorer.restore("{\"theme\":\"DARK\"}", passphrase))
        assertEquals(
            RestoreResult.Invalid,
            restorer.restore(fileWith("{}", app = "SomethingElse"), passphrase)
        )
    }

    @Test
    fun `a damaged envelope is invalid, not a wrong passphrase`() {
        val backup = exported()
        val noBase64 = alter(backup) { it.copy(sealed = it.sealed.copy(data = "***")) }
        assertEquals(RestoreResult.Invalid, restorer().restore(noBase64, passphrase))
        val weak = alter(backup) { it.copy(sealed = it.sealed.copy(iterations = 1)) }
        assertEquals(RestoreResult.Invalid, restorer().restore(weak, passphrase))
    }

    @Test
    fun `sealed content that is not settings is invalid`() {
        assertEquals(RestoreResult.Invalid, restorer().restore(fileWith("not json"), passphrase))
        assertEquals(AppSettings(), newPhone.current())
    }

    @Test
    fun `a file from a newer layout is not opened`() {
        val future = fileWith("{}", format = BACKUP_FORMAT + 1)
        assertEquals(RestoreResult.NewerVersion, restorer().restore(future, passphrase))
        assertEquals(
            RestoreResult.NewerVersion,
            restorer().restore(future, "wrong passphrase".toCharArray())
        )
    }

    @Test
    fun `content from a newer version is not applied`() {
        val future =
            fileWith(
                "{\"version\":${BACKUP_CONTENT_VERSION + 1},\"settings\":{\"theme\":\"DARK\"}}"
            )
        assertEquals(RestoreResult.NewerVersion, restorer().restore(future, passphrase))
        assertEquals(AppSettings(), newPhone.current())
    }

    @Test
    fun `an older backup with fewer settings keeps the rest as it was`() {
        newPhone.update { it.copy(notifyChanges = true, ownEmails = listOf("keep@x.org")) }
        val older = fileWith("{\"version\":1,\"settings\":{\"theme\":\"LIGHT\",\"amoled\":true}}")
        assertEquals(RestoreResult.Restored(), restorer().restore(older, passphrase))
        val settings = newPhone.current()
        assertEquals(ThemeMode.LIGHT, settings.theme)
        assertTrue(settings.amoled)
        assertTrue(settings.notifyChanges)
        assertEquals(listOf("keep@x.org"), settings.ownEmails)
    }

    @Test
    fun `newer additions are ignored and unknown choices keep the current one`() {
        newPhone.update { it.copy(theme = ThemeMode.DARK, initialView = InitialView.MONTH) }
        val content = "{\"version\":1,\"extra\":{\"a\":1},\"settings\":{\"theme\":\"NEON\"," +
            "\"firstDayOfWeek\":\"FUNDAY\",\"initialView\":\"HOLOGRAM\"," +
            "\"inviteCheck\":\"NEVER\"," +
            "\"newSetting\":true,\"amoled\":true}}"
        assertEquals(RestoreResult.Restored(), restorer().restore(fileWith(content), passphrase))
        val settings = newPhone.current()
        assertEquals(ThemeMode.DARK, settings.theme)
        assertEquals(FirstDayOfWeek.LOCALE, settings.firstDayOfWeek)
        assertEquals(InitialView.MONTH, settings.initialView)
        assertEquals(InviteCheckInterval.EVERY_30, settings.inviteCheck)
        assertTrue(settings.amoled)
    }

    @Test
    fun `restored values are mended like any others`() {
        val content =
            "{\"version\":1,\"settings\":{\"defaultDurationMinutes\":0," +
                "\"defaultReminders\":[30,-2,30]," +
                "\"ownEmails\":[\"Ana@Example.com\",\"nonsense\"]," +
                "\"missedWindowHours\":13,\"allDayMinute\":-9}}"
        assertEquals(RestoreResult.Restored(), restorer().restore(fileWith(content), passphrase))
        val settings = newPhone.current()
        assertEquals(5, settings.defaultDurationMinutes)
        assertEquals(listOf(30), settings.defaultReminders)
        assertEquals(listOf("ana@example.com"), settings.ownEmails)
        assertEquals(24, settings.missedWindowHours)
        assertEquals(0, settings.allDayMinute)
    }

    @Test
    fun `a backup of a robust phone restores robust mode, and one without it leaves it alone`() {
        val withRobust =
            fileWith("{\"version\":1,\"settings\":{\"robustMode\":true,\"alarmClock\":true}}")
        restorer().restore(withRobust, passphrase)
        assertTrue(newPhone.current().robustMode && newPhone.current().alarmClock)
        restorer().restore(fileWith("{\"version\":1,\"settings\":{}}"), passphrase)
        assertTrue(newPhone.current().robustMode)
    }

    @Test
    fun `the re-remind option travels, an unknown or missing one leaves the default off`() {
        oldPhone.update { it.copy(reRemind = ReRemindOption.BOTH) }
        val exported = SettingsBackup(oldPhone).export(passphrase)
        assertEquals(RestoreResult.Restored(), restorer().restore(exported, passphrase))
        assertEquals(ReRemindOption.BOTH, newPhone.current().reRemind)

        val fresh = SettingsRepository(FakePreferences(), FakePreferences(), FakePreferences())
        val unknown = fileWith("{\"version\":2,\"settings\":{\"reRemind\":\"EVERY_WEEK\"}}")
        SettingsBackup(fresh).restore(unknown, passphrase)
        assertEquals(ReRemindOption.OFF, fresh.current().reRemind)
        SettingsBackup(fresh).restore(fileWith("{\"version\":2,\"settings\":{}}"), passphrase)
        assertEquals(ReRemindOption.OFF, fresh.current().reRemind)
    }
}
