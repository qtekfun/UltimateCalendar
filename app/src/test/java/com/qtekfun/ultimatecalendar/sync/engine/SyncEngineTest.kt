// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.engine

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.CredentialStore
import com.qtekfun.ultimatecalendar.data.auth.SavedLogin
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.remote.Credentials
import com.qtekfun.ultimatecalendar.data.remote.caldav.CalDavProvider
import com.qtekfun.ultimatecalendar.sync.conflict.event
import com.qtekfun.ultimatecalendar.sync.queue.ProcessResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SyncEngineTest {
    @StartStop
    val server = MockWebServer()

    private lateinit var env: EngineFixtures
    private lateinit var session: SyncSession

    @BeforeEach
    fun setUp() = runTest {
        env = EngineFixtures(server).setUp()
        session =
            SyncSession(SignedInAccount(env.account.serverUrl, env.account.loginName), env.dav)
    }

    @AfterEach
    fun close() = env.db.close()

    private fun engine(
        source: SyncSource = SyncSource { session },
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    ) = SyncEngine(source, env.db, env.push, env.pull, dispatcher, env.clock, lastSync)

    private val lastSync = InMemoryLastSyncStore()

    private val href get() = env.work + "standup.ics"

    @Test
    fun `without an account there is nothing to sync`() = runTest {
        val engine = engine(SyncSource { null })

        assertEquals(SyncOutcome.NoAccount, engine.sync())
        assertEquals(SyncOutcome.NoAccount, engine.lastOutcome.value)
        assertEquals(0, env.fake.requests.size)
    }

    @Test
    fun `sends the queued changes first, then pulls what the server has`() = runTest {
        val engine = engine()
        env.fake.put(env.work + "retro.ics", env.ics(event(title = "Retro")))
        // The very first sync: the calendar is not known yet, so the create waits for the pull.
        assertEquals(SyncOutcome.Ok(ProcessResult()), engine.sync())
        env.createLocally(href, event(title = "Standup"))

        val outcome = engine.sync()

        assertEquals(SyncOutcome.Ok(ProcessResult(done = 1)), outcome)
        assertTrue("SUMMARY:Standup" in env.fake.resources.getValue(href).ics)
        assertEquals("Retro", env.row(env.work + "retro.ics").title)
        assertEquals(outcome, engine.lastOutcome.value)
    }

    @Test
    fun `the account row is made once and keeps the calendar home it found`() = runTest {
        val engine = engine(
            SyncSource {
                SyncSession(SignedInAccount("https://elsewhere.example.com/", "bo"), env.dav)
            }
        )
        // The fake server answers whatever the account: only the rows matter here.
        engine.sync()
        engine.sync()

        val account = env.db.davAccountDao().find("https://elsewhere.example.com/", "bo")
        assertNotNull(account)
        assertEquals(env.fake.home, account?.calendarHome)
        assertNull(env.db.davAccountDao().find("https://elsewhere.example.com/", "cy"))
    }

    @Test
    fun `reports what went wrong`() = runTest {
        val engine = engine()

        env.fake.failures["/remote.php/dav/"] = ArrayDeque(listOf(401))
        assertEquals(SyncOutcome.Unauthorized, engine.sync())

        env.fake.failures["/remote.php/dav/"] = ArrayDeque(listOf(500))
        assertEquals(SyncOutcome.Error("HTTP 500"), engine.sync())

        env.fake.failures["/remote.php/dav/"] = ArrayDeque(listOf(403))
        assertEquals(SyncOutcome.Error("Forbidden"), engine.sync())
        assertEquals(SyncOutcome.Error("Forbidden"), engine.lastOutcome.value)
    }

    @Test
    fun `a good sync records when it finished, a failed one does not`() = runTest {
        val engine = engine()
        assertNull(lastSync.lastOk())

        env.fake.failures["/remote.php/dav/"] = ArrayDeque(listOf(500))
        engine.sync()
        assertNull(lastSync.lastOk())

        engine.sync()
        assertEquals(env.clock.instant(), lastSync.lastOk())
    }

    @Test
    fun `tells while a sync runs`() = runTest {
        val engine = engine()
        assertEquals(false, engine.syncing.value)
        engine.syncing.test {
            assertEquals(false, awaitItem())
            engine.sync()
            assertEquals(true, awaitItem())
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `an unreachable server is offline`() = runTest {
        val engine = engine()
        server.close()

        assertEquals(SyncOutcome.Offline, engine.sync())
    }

    @Test
    fun `syncs never overlap`() = runTest {
        var running = 0
        var most = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = engine(
            SyncSource {
                running++
                most = maxOf(most, running)
                delay(100)
                running--
                null
            },
            dispatcher
        )

        val first = async(start = CoroutineStart.UNDISPATCHED) { engine.sync() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { engine.sync() }

        assertEquals(SyncOutcome.NoAccount, first.await())
        assertEquals(SyncOutcome.NoAccount, second.await())
        assertEquals(1, most)
    }

    @Test
    fun `the last outcome is observable`() = runTest {
        val engine = engine(SyncSource { null })

        engine.lastOutcome.test {
            assertNull(awaitItem())
            engine.sync()
            assertEquals(SyncOutcome.NoAccount, awaitItem())
        }
    }

    @Test
    fun `the session source restores the login, then connects the account`() = runTest {
        val store = mockk<CredentialStore>()
        every { store.load() } returns
            SavedLogin("https://cloud.example.com/", Credentials("ana", "secret"))
        val accounts = AccountSession(store)
        val provider = CalDavProvider(OkHttpClient(), accounts, Dispatchers.Unconfined)
        val source = SessionSyncSource(accounts, provider)

        val opened = source.open()

        assertEquals(SignedInAccount("https://cloud.example.com/", "ana"), opened?.account)
        // Already restored: the second call does not read the store again.
        source.open()
        verify(exactly = 1) { store.load() }
    }

    @Test
    fun `the session source has nothing when nobody is signed in or the address is refused`() =
        runTest {
            val store = mockk<CredentialStore>()
            every { store.load() } returns null
            val accounts = AccountSession(store)
            val provider = CalDavProvider(OkHttpClient(), accounts, Dispatchers.Unconfined)

            assertNull(SessionSyncSource(accounts, provider).open())

            every { store.load() } returns
                SavedLogin("http://insecure.example.com/", Credentials("ana", "x"))
            accounts.restore()
            assertNull(SessionSyncSource(accounts, provider).open())
        }
}
