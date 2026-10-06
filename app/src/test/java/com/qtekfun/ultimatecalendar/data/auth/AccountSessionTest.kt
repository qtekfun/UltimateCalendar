// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.auth

import com.qtekfun.ultimatecalendar.data.remote.Credentials
import com.qtekfun.ultimatecalendar.data.remote.ServerUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AccountSessionTest {
    private val storage = FakeLoginStorage()
    private val store = CredentialStore(storage, FakeCipher())
    private val server =
        (ServerUrl.parse("https://cloud.example.com/nextcloud") as ServerUrl.ParseResult.Valid).url

    @Test
    fun `signing in keeps the credentials in memory and stores them encrypted`() {
        val session = AccountSession(store)

        val account = session.signIn(server, Credentials("ana", "pw"))

        assertEquals(SignedInAccount("https://cloud.example.com/nextcloud/", "ana"), account)
        assertEquals(account, session.activeAccount.value)
        assertEquals("pw", session.credentials()?.appPassword)
        assertEquals("pw", store.load()?.credentials?.appPassword)
    }

    @Test
    fun `restoring after a restart brings back the stored account`() {
        AccountSession(store).signIn(server, Credentials("ana", "pw"))
        val restarted = AccountSession(store)
        assertNull(restarted.credentials())

        val restored = restarted.restore()

        assertEquals("ana", restored?.loginName)
        assertEquals("pw", restarted.credentials()?.appPassword)
        assertEquals(restored, restarted.activeAccount.value)
    }

    @Test
    fun `restoring without a stored login leaves nobody signed in`() {
        val session = AccountSession(store)

        assertNull(session.restore())
        assertNull(session.activeAccount.value)
        assertNull(session.credentials())
    }

    @Test
    fun `signing out forgets the account and deletes the stored login`() {
        val session = AccountSession(store)
        session.signIn(server, Credentials("ana", "pw"))

        session.signOut()

        assertNull(session.activeAccount.value)
        assertNull(session.credentials())
        assertNull(storage.read())
    }

    @Test
    fun `the password never shows in the text of the credentials`() {
        assertEquals(
            "Credentials(loginName=ana, appPassword=***)",
            Credentials("ana", "pw").toString()
        )
    }
}
