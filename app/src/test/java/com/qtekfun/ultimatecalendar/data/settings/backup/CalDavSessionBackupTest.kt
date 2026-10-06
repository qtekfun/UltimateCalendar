// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings.backup

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.CredentialStore
import com.qtekfun.ultimatecalendar.data.auth.FakeCipher
import com.qtekfun.ultimatecalendar.data.auth.FakeLoginStorage
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.remote.Credentials
import com.qtekfun.ultimatecalendar.data.remote.ServerUrl
import com.qtekfun.ultimatecalendar.data.remote.caldav.CalDavProvider
import com.qtekfun.ultimatecalendar.sync.engine.FakeCalDav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Signing in again from a backup, against the fake CalDAV server behind MockWebServer. */
class CalDavSessionBackupTest {
    @StartStop
    val server = MockWebServer()

    private val fake = FakeCalDav()
    private val storage = FakeLoginStorage()
    private val session = AccountSession(CredentialStore(storage, FakeCipher()))
    private val provider = CalDavProvider(
        OkHttpClient(),
        { session.credentials() },
        Dispatchers.Unconfined
    )
    private val backup = CalDavSessionBackup(session, provider, Dispatchers.Unconfined)
    private val root get() = server.url("/").toString()

    @BeforeEach
    fun setUp() {
        server.dispatcher = fake
    }

    private fun signInHere(password: String = "app-password") {
        val url = (ServerUrl.parse(root, allowInsecure = true) as ServerUrl.ParseResult.Valid).url
        session.signIn(url, Credentials("ana", password))
    }

    @Test
    fun `collects the signed-in account with its app password`() = runBlocking {
        signInHere("s3cret")

        assertEquals(BackupSession(root, "ana", "s3cret"), backup.collect())
    }

    @Test
    fun `collects the stored login of a fresh process`() = runBlocking {
        signInHere("s3cret")
        val restarted = AccountSession(CredentialStore(storage, FakeCipher()))

        val collected = CalDavSessionBackup(restarted, provider, Dispatchers.Unconfined).collect()

        assertEquals(BackupSession(root, "ana", "s3cret"), collected)
    }

    @Test
    fun `nobody signed in means nothing to collect`() = runBlocking {
        assertNull(backup.collect())
    }

    @Test
    fun `the password never shows when the session is printed`() {
        val text = BackupSession(root, "ana", "s3cret").toString()

        assertFalse("s3cret" in text)
        assertTrue("ana" in text)
    }

    @Test
    fun `a session is equal to another with the same server, login and password`() {
        val one = BackupSession(root, "ana", "pw")

        assertEquals(one, BackupSession(root, "ana", "pw"))
        assertEquals(one.hashCode(), BackupSession(root, "ana", "pw").hashCode())
        assertFalse(one == BackupSession(root, "ana", "other"))
        assertFalse(one.equals("ana"))
    }

    @Test
    fun `a credential the server accepts signs in and is stored encrypted`() = runBlocking {
        val result = backup.restore(
            BackupSession(root, "ana", "app-password"),
            allowInsecure = true
        )

        assertEquals(SessionRestoreResult.SignedIn(SignedInAccount(root, "ana")), result)
        assertEquals("ana", session.activeAccount.value?.loginName)
        assertEquals("app-password", session.credentials()?.appPassword)
        // On disk only the ciphertext is ever kept.
        assertFalse("app-password" in storage.rawText())
    }

    @Test
    fun `a credential the server rejects is dropped without being stored`() = runBlocking {
        fake.failures["/remote.php/dav/"] = ArrayDeque(listOf(401))

        val result = backup.restore(BackupSession(root, "ana", "revoked"), allowInsecure = true)

        assertEquals(SessionRestoreResult.Rejected, result)
        assertNull(session.activeAccount.value)
        assertNull(session.credentials())
        assertEquals("", storage.rawText())
    }

    @Test
    fun `a forbidden answer counts as rejected too`() = runBlocking {
        fake.failures["/remote.php/dav/"] = ArrayDeque(listOf(403))

        assertEquals(
            SessionRestoreResult.Rejected,
            backup.restore(BackupSession(root, "ana", "pw"), allowInsecure = true)
        )
    }

    @Test
    fun `a server that cannot be reached or fails is not a rejection, and nothing is stored`() =
        runBlocking {
            fake.failures["/remote.php/dav/"] = ArrayDeque(listOf(500))
            assertEquals(
                SessionRestoreResult.Unreachable,
                backup.restore(BackupSession(root, "ana", "pw"), allowInsecure = true)
            )

            val address = root
            server.close()
            assertEquals(
                SessionRestoreResult.Unreachable,
                backup.restore(BackupSession(address, "ana", "pw"), allowInsecure = true)
            )
            assertNull(session.activeAccount.value)
            assertEquals("", storage.rawText())
        }

    @Test
    fun `plain http and garbage addresses are refused before any request`() = runBlocking {
        // Not through the test hook: the real rule is https only.
        assertEquals(
            SessionRestoreResult.Invalid,
            backup.restore(BackupSession("http://cloud.example.com/", "ana", "pw"))
        )
        assertEquals(
            SessionRestoreResult.Invalid,
            backup.restore(BackupSession("https://", "ana", "pw"))
        )
        assertEquals(0, fake.requests.size)
        assertNull(session.activeAccount.value)
    }

    @Test
    fun `somebody already signed in is left alone`() = runBlocking {
        signInHere("mine")

        val result = backup.restore(BackupSession(root, "bo", "theirs"), allowInsecure = true)

        assertEquals(SessionRestoreResult.AlreadySignedIn, result)
        assertEquals("ana", session.activeAccount.value?.loginName)
        assertEquals("mine", session.credentials()?.appPassword)
    }
}
