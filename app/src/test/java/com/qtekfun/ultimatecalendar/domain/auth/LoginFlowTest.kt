// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.auth

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.CredentialStore
import com.qtekfun.ultimatecalendar.data.auth.FakeCipher
import com.qtekfun.ultimatecalendar.data.auth.FakeLoginStorage
import com.qtekfun.ultimatecalendar.data.auth.LoginFlowApiFactory
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.remote.NextcloudJson
import com.qtekfun.ultimatecalendar.data.remote.ServerUrl
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Runs in real time with short intervals: virtual time would fire the login timeout while a real
 * HTTP request is still in flight.
 */
class LoginFlowTest {
    @StartStop
    val server = MockWebServer()

    private val nextcloud by lazy { FakeNextcloud(server).also { server.dispatcher = it } }
    private val storage = FakeLoginStorage()
    private val store = CredentialStore(storage, FakeCipher())
    private val session = AccountSession(store)
    private val flow = LoginFlow(LoginFlowApiFactory(OkHttpClient(), NextcloudJson), session)

    private fun localServer(): ServerUrl {
        nextcloud
        val parsed = ServerUrl.parse(server.url("/nextcloud/").toString(), allowInsecure = true)
        return (parsed as ServerUrl.ParseResult.Valid).url
    }

    private fun states(timeoutMs: Long = 2_000) = runBlocking {
        flow.login(
            localServer(),
            pollInterval = 10.milliseconds,
            timeout = timeoutMs.milliseconds
        ).toList()
    }

    @Test
    fun `logs in through the browser and stores the sign-in with encrypted credentials`() =
        runBlocking {
            val states = states()

            assertEquals(LoginState.CheckingServer, states[0])
            assertEquals(
                LoginState.WaitingForBrowser(server.url("/nextcloud/login/v2/flow/abc").toString()),
                states[1]
            )
            val account = (states[2] as LoginState.LoggedIn).account
            assertEquals(3, states.size)
            assertEquals(SignedInAccount("https://cloud.example.com/", "username"), account)
            assertEquals(account, session.activeAccount.value)
            assertEquals("username", session.credentials()?.loginName)
            val saved = store.load()!!
            assertEquals(session.credentials()?.appPassword, saved.credentials.appPassword)
            assertEquals("https://cloud.example.com/", saved.serverUrl)
            assertFalse(storage.rawText().contains(saved.credentials.appPassword))
        }

    @Test
    fun `refuses plain http and malformed addresses without any request`() = runBlocking {
        assertEquals(
            listOf(LoginState.Failed(LoginError.INSECURE_URL)),
            flow.login("http://cloud.example.com").toList()
        )
        assertEquals(
            listOf(LoginState.Failed(LoginError.INVALID_URL)),
            flow.login("https://").toList()
        )
    }

    @Test
    fun `reports a server that is not Nextcloud`() {
        nextcloud.statusBody = """{"installed": false}"""
        assertEquals(LoginState.Failed(LoginError.NOT_NEXTCLOUD), states().last())

        nextcloud.statusBody = "<html>Welcome to nginx</html>"
        assertEquals(LoginState.Failed(LoginError.NOT_NEXTCLOUD), states().last())

        nextcloud.statusCode = 404
        assertEquals(LoginState.Failed(LoginError.NOT_NEXTCLOUD), states().last())
    }

    @Test
    fun `reports an unreachable server`() {
        val address = localServer()
        server.close()

        val states =
            runBlocking { flow.login(address, 10.milliseconds, 2_000.milliseconds).toList() }

        assertEquals(
            listOf(LoginState.CheckingServer, LoginState.Failed(LoginError.UNREACHABLE)),
            states
        )
    }

    @Test
    fun `reports a server that refuses to start the login`() {
        nextcloud.startCode = 500

        assertEquals(LoginState.Failed(LoginError.UNKNOWN), states().last())
    }

    @Test
    fun `expires when the user never finishes in the browser`() = runBlocking {
        nextcloud.pollsBeforeLogin = Int.MAX_VALUE

        val states = states(timeoutMs = 200)

        assertEquals(LoginState.Failed(LoginError.EXPIRED), states.last())
        assertTrue(nextcloud.requests.count { it.endsWith("/login/v2/poll") } > 1)
        assertNull(session.activeAccount.value)
    }

    @Test
    fun `cancelling stops the polling`() = runBlocking {
        nextcloud.pollsBeforeLogin = Int.MAX_VALUE

        val firstWaiting = flow.login(localServer(), 10.milliseconds, 60_000.milliseconds)
            .first { it is LoginState.WaitingForBrowser }
        val pollsAtCancel = nextcloud.requests.count { it.endsWith("/login/v2/poll") }
        Thread.sleep(100)

        assertTrue(firstWaiting is LoginState.WaitingForBrowser)
        assertEquals(pollsAtCancel, nextcloud.requests.count { it.endsWith("/login/v2/poll") })
    }

    @Test
    fun `upgrades an http poll endpoint on the server's host to https`() {
        val server = (
            ServerUrl.parse(
                "https://cloud.example.com/nextcloud"
            ) as ServerUrl.ParseResult.Valid
            ).url

        assertEquals(
            "https://cloud.example.com/nextcloud/login/v2/poll",
            LoginFlow.securePollEndpoint("http://cloud.example.com/nextcloud/login/v2/poll", server)
        )
        assertEquals(
            "http://elsewhere.example/poll",
            LoginFlow.securePollEndpoint("http://elsewhere.example/poll", server)
        )
        assertEquals("not a url", LoginFlow.securePollEndpoint("not a url", server))
    }
}
