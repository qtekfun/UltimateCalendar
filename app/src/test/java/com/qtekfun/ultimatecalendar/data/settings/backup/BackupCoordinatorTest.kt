// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings.backup

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.CredentialStore
import com.qtekfun.ultimatecalendar.data.auth.FakeCipher
import com.qtekfun.ultimatecalendar.data.auth.FakeLoginStorage
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.entity.CalendarSettingsEntity
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.remote.Credentials
import com.qtekfun.ultimatecalendar.data.remote.ServerUrl
import com.qtekfun.ultimatecalendar.data.remote.caldav.CalDavProvider
import com.qtekfun.ultimatecalendar.data.settings.AppSettings
import com.qtekfun.ultimatecalendar.data.settings.FakePreferences
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.data.settings.ThemeMode
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.data.subscriptions.RestoredSubscriptions
import com.qtekfun.ultimatecalendar.data.subscriptions.SubscriptionRepository
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.sync.engine.FakeCalDav
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Export and restore as the app does them, end to end, with the CalDAV sign-in in the file. */
class BackupCoordinatorTest {
    @StartStop
    val server = MockWebServer()

    private val fake = FakeCalDav()
    private val passphrase = "correct horse".toCharArray()
    private val root get() = server.url("/").toString()
    private val databases = mutableListOf<UltimateCalendarDatabase>()

    @AfterEach
    fun close() = databases.forEach { it.close() }

    /** One phone: its own settings, session and database. */
    private inner class Phone {
        val settings = SettingsRepository(FakePreferences(), FakePreferences(), FakePreferences())
        val storage = FakeLoginStorage()
        val session = AccountSession(CredentialStore(storage, FakeCipher()))
        val db = inMemoryDatabase().also { databases += it }
        val provider =
            CalDavProvider(OkHttpClient(), { session.credentials() }, Dispatchers.Unconfined)
        val sessions = CalDavSessionBackup(session, provider, Dispatchers.Unconfined)
        val feeds = mockk<SubscriptionRepository> {
            coEvery { forBackup() } returns emptyList()
            coEvery { restore(any()) } returns RestoredSubscriptions(added = 0, refused = 0)
        }
        val coordinator = BackupCoordinator(
            SettingsBackup(settings),
            CalendarOverridesBackup(
                FakeCalendarSource(
                    listOf(
                        CalendarInfo(
                            CalendarId(1),
                            CalendarAccount("ana@gmail.com", "com.google"),
                            "Personal",
                            0xFF0000FF.toInt(),
                            CalendarAccess.OWNER
                        )
                    )
                ),
                db.calendarSettingsDao(),
                Dispatchers.Unconfined
            ),
            sessions,
            feeds,
            Dispatchers.Unconfined
        )

        fun signIn(password: String = "app-password") {
            val url =
                (ServerUrl.parse(root, allowInsecure = true) as ServerUrl.ParseResult.Valid).url
            session.signIn(url, Credentials("ana", password))
        }
    }

    /** A restore whose session check may talk to the plain-http fake. */
    private suspend fun Phone.restoreAllowingHttp(text: String): RestoreOutcome {
        val restored = SettingsBackup(settings).restore(text, passphrase)
        val sessionResult = (restored as? RestoreResult.Restored)?.session
            ?.let { sessions.restore(it, allowInsecure = true) }
        return RestoreOutcome(restored, 0, sessionResult)
    }

    @Test
    fun `without the switch the file carries no sign-in`() = runBlocking {
        val old = Phone().apply { signIn() }

        val file = old.coordinator.export(passphrase, includeSession = false)

        val restored = SettingsBackup(Phone().settings).restore(file, passphrase)
        assertNull((restored as RestoreResult.Restored).session)
    }

    @Test
    fun `with the switch the new phone signs in again and the secret reaches only the store`() =
        runBlocking {
            server.dispatcher = fake
            val old = Phone().apply {
                signIn("the-app-password")
                settings.update { it.copy(theme = ThemeMode.DARK) }
            }
            val file = old.coordinator.export(passphrase, includeSession = true)
            val new = Phone()

            val outcome = new.restoreAllowingHttp(file)

            assertTrue(outcome.session is SessionRestoreResult.SignedIn)
            assertEquals(ThemeMode.DARK, new.settings.current().theme)
            assertEquals("ana", new.session.activeAccount.value?.loginName)
            assertEquals("the-app-password", new.session.credentials()?.appPassword)
            assertFalse("the-app-password" in new.storage.rawText())
            assertFalse("the-app-password" in file)
        }

    @Test
    fun `a server that rejects the credential shows it and the rest is restored anyway`() =
        runBlocking {
            server.dispatcher = fake
            val old = Phone().apply {
                signIn("revoked")
                settings.update { it.copy(theme = ThemeMode.DARK, amoled = true) }
            }
            val file = old.coordinator.export(passphrase, includeSession = true)
            fake.failures["/remote.php/dav/"] = ArrayDeque(listOf(401))
            val new = Phone()

            val outcome = new.restoreAllowingHttp(file)

            assertEquals(SessionRestoreResult.Rejected, outcome.session)
            assertEquals(ThemeMode.DARK, new.settings.current().theme)
            assertTrue(new.settings.current().amoled)
            assertNull(new.session.activeAccount.value)
            assertEquals("", new.storage.rawText())
        }

    @Test
    fun `a backup with a session that is refused by the https rule is restored without it`() =
        runBlocking {
            val old = Phone().apply {
                signIn()
                settings.update { it.copy(theme = ThemeMode.LIGHT) }
            }
            val file = old.coordinator.export(passphrase, includeSession = true)
            val new = Phone()

            // The real restore (no test hook): the fake's address is plain http.
            val outcome = new.coordinator.restore(file, passphrase)

            assertEquals(SessionRestoreResult.Invalid, outcome.session)
            assertEquals(ThemeMode.LIGHT, new.settings.current().theme)
            assertNull(new.session.activeAccount.value)
        }

    @Test
    fun `a backup without a session leaves the sign-in out of the outcome`() = runBlocking {
        val old = Phone().apply { settings.update { it.copy(amoled = true) } }
        val file = old.coordinator.export(passphrase, includeSession = true)
        val new = Phone()

        val outcome = new.coordinator.restore(file, passphrase)

        assertEquals(RestoreOutcome(RestoreResult.Restored(), 0, null), outcome)
        assertTrue(new.settings.current().amoled)
    }

    @Test
    fun `a wrong passphrase restores nothing, not even a sign-in`() = runBlocking {
        server.dispatcher = fake
        val old = Phone().apply { signIn() }
        val file = old.coordinator.export(passphrase, includeSession = true)
        val new = Phone()

        val outcome = new.coordinator.restore(file, "wrong passphrase".toCharArray())

        assertEquals(RestoreOutcome(RestoreResult.WrongPassphrase), outcome)
        assertNull(new.session.activeAccount.value)
        assertEquals(0, fake.requests.size)
        assertEquals(AppSettings(), new.settings.current())
    }

    @Test
    fun `the calendar overrides are counted as missing when their calendar is not there yet`() =
        runBlocking {
            val old = Phone()
            old.db.calendarSettingsDao().save(CalendarSettingsEntity(1, "Mine", null, null))
            val file = old.coordinator.export(passphrase, includeSession = false)
            // The new phone has no Google calendar yet.
            val new = Phone()
            val bare = BackupCoordinator(
                SettingsBackup(new.settings),
                CalendarOverridesBackup(
                    FakeCalendarSource(emptyList()),
                    new.db.calendarSettingsDao(),
                    Dispatchers.Unconfined
                ),
                new.sessions,
                new.feeds,
                Dispatchers.Unconfined
            )

            val outcome = bare.restore(file, passphrase)

            assertEquals(1, outcome.missingCalendars)
        }

    @Test
    fun `subscriptions travel with the backup, refused ones count as missing calendars`() =
        runBlocking {
            val feed = BackupSubscription("Work", "https://cal.example.com/work.ics")
            val old = Phone()
            coEvery { old.feeds.forBackup() } returns listOf(feed)
            val file = old.coordinator.export(passphrase, includeSession = false)
            val new = Phone()
            coEvery { new.feeds.restore(listOf(feed)) } returns
                RestoredSubscriptions(added = 0, refused = 2)

            val outcome = new.coordinator.restore(file, passphrase)

            assertEquals(2, outcome.missingCalendars)
            coVerify { new.feeds.restore(listOf(feed)) }
        }
}
