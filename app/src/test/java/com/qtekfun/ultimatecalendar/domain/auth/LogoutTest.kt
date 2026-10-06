// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.auth

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.CredentialStore
import com.qtekfun.ultimatecalendar.data.auth.FakeCipher
import com.qtekfun.ultimatecalendar.data.auth.FakeLoginStorage
import com.qtekfun.ultimatecalendar.data.auth.LoginFlowApiFactory
import com.qtekfun.ultimatecalendar.data.remote.Credentials
import com.qtekfun.ultimatecalendar.data.remote.NextcloudJson
import com.qtekfun.ultimatecalendar.data.remote.ServerUrl
import java.util.Base64
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogoutTest {
    @StartStop
    val server = MockWebServer()

    private val storage = FakeLoginStorage()
    private val store = CredentialStore(storage, FakeCipher())
    private val session = AccountSession(store)
    private val logout = Logout(LoginFlowApiFactory(OkHttpClient(), NextcloudJson), session)

    /** Signs in an account whose server is the local MockWebServer. */
    private fun signIn() {
        val url = ServerUrl.parse(
            server.url("/nextcloud/").toString(),
            allowInsecure = true
        ) as ServerUrl.ParseResult.Valid
        session.signIn(url.url, Credentials("ana", "app-password"))
    }

    @Test
    fun `revokes the app password and forgets the account`() = runBlocking {
        server.enqueue(MockResponse(200))
        signIn()

        logout.run(allowInsecure = true)

        val request = server.takeRequest()
        assertEquals(
            "DELETE /nextcloud/ocs/v2.php/core/apppassword",
            "${request.method} ${request.target}"
        )
        val basic = request.headers["Authorization"].orEmpty().removePrefix("Basic ")
        assertEquals("ana:app-password", String(Base64.getDecoder().decode(basic)))
        assertNull(session.activeAccount.value)
        assertNull(session.credentials())
        assertNull(store.load())
    }

    @Test
    fun `still logs out when the server cannot be reached`() = runBlocking {
        signIn()
        server.close()

        logout.run(allowInsecure = true)

        assertNull(session.activeAccount.value)
        assertNull(store.load())
    }

    @Test
    fun `uses the stored credentials after an app restart`() = runBlocking {
        server.enqueue(MockResponse(200))
        signIn()
        val restarted = AccountSession(store)
        val afterRestart = Logout(LoginFlowApiFactory(OkHttpClient(), NextcloudJson), restarted)

        afterRestart.run(allowInsecure = true)

        assertTrue(server.takeRequest().headers["Authorization"]?.startsWith("Basic ") == true)
        assertNull(store.load())
    }

    @Test
    fun `does not call the server when the stored address is not valid https`() = runBlocking {
        signIn()

        logout()

        assertEquals(0, server.requestCount)
        assertNull(session.activeAccount.value)
    }

    @Test
    fun `does nothing without an account`() = runBlocking {
        logout()

        assertNull(session.activeAccount.value)
        assertEquals(0, server.requestCount)
    }
}
