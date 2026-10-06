// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.domain.invitations.ReRemindOption
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.settings.FirstDayOfWeek
import com.qtekfun.ultimatecalendar.domain.settings.InitialView
import com.qtekfun.ultimatecalendar.domain.settings.InviteCheckInterval
import com.qtekfun.ultimatecalendar.sync.CheckInterval
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsRepositoryTest {
    private val prefs = FakePreferences()
    private val legacy = FakePreferences()
    private val legacyFirstRun = FakePreferences()
    private val repository = SettingsRepository(prefs, legacy, legacyFirstRun)

    @Test
    fun `a new install has the defaults`() = runTest {
        val settings = repository.settings.first()
        assertEquals(AppSettings(), settings)
        assertEquals(ThemeMode.SYSTEM, settings.theme)
        assertTrue(settings.dynamicColor)
        assertFalse(settings.amoled)
        assertEquals(FirstDayOfWeek.LOCALE, settings.firstDayOfWeek)
        assertEquals(InitialView.AGENDA, settings.initialView)
        assertNull(settings.defaultCalendar)
        assertEquals(60, settings.defaultDurationMinutes)
        assertEquals(listOf(10), settings.defaultReminders)
        assertEquals(listOf(0), settings.defaultAllDayReminders)
        assertEquals(InviteCheckInterval.EVERY_30, settings.inviteCheck)
        assertEquals(emptyList<String>(), settings.ownEmails)
        assertFalse(settings.notifyChanges || settings.notifyCancellations)
        assertEquals(ReRemindOption.OFF, settings.reRemind)
        assertEquals(24, settings.missedWindowHours)
        assertFalse(settings.alarmClock || settings.robustMode)
        assertEquals(9 * 60, settings.allDayMinute)
    }

    @Test
    fun `every setting is kept and read back`() = runTest {
        val changed = AppSettings(
            theme = ThemeMode.DARK,
            amoled = true,
            dynamicColor = false,
            firstDayOfWeek = FirstDayOfWeek.SATURDAY,
            initialView = InitialView.MONTH,
            defaultCalendar = CalendarId(7),
            defaultDurationMinutes = 90,
            defaultReminders = listOf(5, 30),
            defaultAllDayReminders = listOf(0, 1440),
            inviteCheck = InviteCheckInterval.MANUAL,
            ownEmails = listOf("ana@example.com", "b@x.org"),
            notifyChanges = true,
            notifyCancellations = true,
            reRemind = ReRemindOption.BOTH,
            missedWindowHours = 0,
            alarmClock = true,
            robustMode = true,
            allDayMinute = 8 * 60 + 30
        )
        repository.update { changed }
        assertEquals(changed, repository.settings.first())
        assertEquals(changed, SettingsRepository(prefs, legacy, legacyFirstRun).current())
    }

    @Test
    fun `empty lists stay empty instead of going back to the defaults`() = runTest {
        repository.update {
            it.copy(defaultReminders = emptyList(), defaultAllDayReminders = emptyList())
        }
        val settings = repository.settings.first()
        assertEquals(emptyList<Int>(), settings.defaultReminders)
        assertEquals(emptyList<Int>(), settings.defaultAllDayReminders)
    }

    @Test
    fun `the default calendar can be cleared`() = runTest {
        repository.update { it.copy(defaultCalendar = CalendarId(3)) }
        assertEquals(CalendarId(3), repository.current().defaultCalendar)
        repository.update { it.copy(defaultCalendar = null) }
        assertNull(repository.current().defaultCalendar)
    }

    @Test
    fun `values are mended before they are kept`() = runTest {
        repository.update {
            it.copy(
                defaultDurationMinutes = 0,
                defaultReminders = listOf(30, -1, 30, 5),
                ownEmails = listOf("ANA@Example.com", "not an address"),
                missedWindowHours = 13,
                allDayMinute = 99_999,
                defaultCalendar = CalendarId(-4)
            )
        }
        val settings = repository.settings.first()
        assertEquals(5, settings.defaultDurationMinutes)
        assertEquals(listOf(5, 30), settings.defaultReminders)
        assertEquals(listOf("ana@example.com"), settings.ownEmails)
        assertEquals(24, settings.missedWindowHours)
        assertEquals(23 * 60 + 59, settings.allDayMinute)
        assertNull(settings.defaultCalendar)
    }

    @Test
    fun `damaged stored values fall back instead of reaching the app`() {
        prefs.values["theme"] = "NEON"
        prefs.values["first_day_of_week"] = "FUNDAY"
        prefs.values["initial_view"] = ""
        prefs.values["invite_check"] = "SOMETIMES"
        prefs.values["default_reminders"] = "10,abc,,-3,20"
        prefs.values["own_emails"] = ",ana@example.com,,garbage"
        prefs.values["default_duration_minutes"] = 100_000
        prefs.values["missed_window_hours"] = 5
        val settings = repository.current()
        assertEquals(ThemeMode.SYSTEM, settings.theme)
        assertEquals(FirstDayOfWeek.LOCALE, settings.firstDayOfWeek)
        assertEquals(InitialView.AGENDA, settings.initialView)
        assertEquals(InviteCheckInterval.EVERY_30, settings.inviteCheck)
        assertEquals(listOf(10, 20), settings.defaultReminders)
        assertEquals(listOf("ana@example.com"), settings.ownEmails)
        assertEquals(24 * 60, settings.defaultDurationMinutes)
        assertEquals(24, settings.missedWindowHours)
    }

    @Test
    fun `changes are emitted once each`() = runTest {
        repository.settings.test {
            assertEquals(AppSettings(), awaitItem())
            repository.update { it.copy(amoled = true) }
            assertTrue(awaitItem().amoled)
            // The same value again changes nothing worth emitting.
            repository.update { it.copy(amoled = true) }
            repository.update { it.copy(theme = ThemeMode.LIGHT) }
            assertEquals(ThemeMode.LIGHT, awaitItem().theme)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `restore replaces everything`() = runTest {
        repository.update { it.copy(amoled = true, ownEmails = listOf("old@x.org")) }
        repository.restore(AppSettings(theme = ThemeMode.LIGHT))
        assertEquals(AppSettings(theme = ThemeMode.LIGHT), repository.current())
    }

    @Test
    fun `robust mode kept by the first reminder settings is moved, not lost`() {
        legacy.values["robust_mode"] = true
        val migrated = SettingsRepository(prefs, legacy, legacyFirstRun)
        assertTrue(migrated.current().robustMode)
        assertFalse(legacy.contains("robust_mode"))
        // Once moved it belongs to the settings: a later run does not touch it again.
        migrated.update { it.copy(robustMode = false) }
        assertFalse(SettingsRepository(prefs, legacy, legacyFirstRun).current().robustMode)
    }

    @Test
    fun `an old robust mode switched off migrates as off`() {
        legacy.values["robust_mode"] = false
        assertFalse(SettingsRepository(prefs, legacy, legacyFirstRun).current().robustMode)
        assertFalse(legacy.contains("robust_mode"))
    }

    @Test
    fun `a value already in the settings wins over an old one`() {
        prefs.values["robust_mode"] = false
        legacy.values["robust_mode"] = true
        assertFalse(SettingsRepository(prefs, legacy, legacyFirstRun).current().robustMode)
        assertFalse(legacy.contains("robust_mode"))
    }

    @Test
    fun `nothing to migrate leaves the settings alone`() {
        assertFalse(prefs.contains("robust_mode"))
        SettingsRepository(prefs, legacy, legacyFirstRun)
        assertFalse(prefs.contains("robust_mode"))
    }

    @Test
    fun `a new install has not seen the wizard, and marking it done is kept`() {
        assertFalse(repository.isDone())
        repository.markDone()
        assertTrue(repository.isDone())
        assertTrue(SettingsRepository(prefs, legacy, legacyFirstRun).isDone())
        // It is not a setting: nothing of it travels in AppSettings.
        assertEquals(AppSettings(), repository.current())
    }

    @Test
    fun `a wizard already seen under the old flag is not shown again`() {
        legacyFirstRun.values["wizard_shown"] = true
        val migrated = SettingsRepository(prefs, legacy, legacyFirstRun)
        assertTrue(migrated.isDone())
        assertFalse(legacyFirstRun.contains("wizard_shown"))
        assertTrue(SettingsRepository(prefs, legacy, legacyFirstRun).isDone())
    }

    @Test
    fun `an old flag that says not seen leaves the wizard to show`() {
        legacyFirstRun.values["wizard_shown"] = false
        assertFalse(SettingsRepository(prefs, legacy, legacyFirstRun).isDone())
        assertFalse(legacyFirstRun.contains("wizard_shown"))
    }

    @Test
    fun `the invitation check reads the interval and the aliases of the settings`() = runTest {
        val check = RepositoryInvitationCheckSettings(repository)
        check.intervals.test {
            assertEquals(CheckInterval.HALF_HOUR, awaitItem())
            repository.update { it.copy(inviteCheck = InviteCheckInterval.EVERY_60) }
            assertEquals(CheckInterval.HOUR, awaitItem())
            // A change of something else is not a new interval.
            repository.update { it.copy(amoled = true) }
            expectNoEvents()
            repository.update { it.copy(inviteCheck = InviteCheckInterval.MANUAL) }
            assertEquals(CheckInterval.MANUAL_ONLY, awaitItem())
            repository.update { it.copy(inviteCheck = InviteCheckInterval.EVERY_15) }
            assertEquals(CheckInterval.QUARTER_HOUR, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(emptySet<String>(), check.aliases())
        repository.update { it.copy(ownEmails = listOf("Ana@Example.com", "b@x.org")) }
        assertEquals(setOf("ana@example.com", "b@x.org"), check.aliases())
    }

    @Test
    fun `the reminders read their part of the settings`() = runTest {
        val source = RepositoryReminderSettings(repository)
        source.setRobustMode(true)
        repository.update {
            it.copy(allDayMinute = 8 * 60 + 30, missedWindowHours = 48, alarmClock = true)
        }
        val reminders = source.settings.first()
        assertEquals(LocalTime.of(8, 30), reminders.allDayTime)
        assertEquals(48, reminders.missedWindowHours)
        assertTrue(reminders.alarmClock && reminders.robustMode)
        source.setRobustMode(false)
        assertFalse(source.settings.first().robustMode)
    }

    @Test
    fun `the app's own settings do not leak into the reminders when robust mode is toggled`() =
        runTest {
            repository.update { it.copy(theme = ThemeMode.DARK, ownEmails = listOf("a@x.org")) }
            RepositoryReminderSettings(repository).setRobustMode(true)
            val settings = repository.current()
            assertEquals(ThemeMode.DARK, settings.theme)
            assertEquals(listOf("a@x.org"), settings.ownEmails)
        }

    @Test
    fun `an unknown re-remind value reads as off, and the reminders see the option`() = runTest {
        prefs.values["re_remind"] = "EVERY_HOUR"
        assertEquals(ReRemindOption.OFF, repository.current().reRemind)

        repository.update { it.copy(reRemind = ReRemindOption.DAY_BEFORE) }

        assertEquals(
            ReRemindOption.DAY_BEFORE,
            RepositoryReminderSettings(repository).settings.first().reRemind
        )
    }
}
