// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.account

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.CredentialStore
import com.qtekfun.ultimatecalendar.data.auth.FakeCipher
import com.qtekfun.ultimatecalendar.data.auth.FakeLoginStorage
import com.qtekfun.ultimatecalendar.data.auth.LoginFlowApiFactory
import com.qtekfun.ultimatecalendar.data.remote.NextcloudJson
import com.qtekfun.ultimatecalendar.data.remote.ServerUrl
import com.qtekfun.ultimatecalendar.domain.auth.FakeNextcloud
import com.qtekfun.ultimatecalendar.domain.auth.LoginError
import com.qtekfun.ultimatecalendar.domain.auth.LoginFlow
import com.qtekfun.ultimatecalendar.domain.auth.LoginState
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The login screen's state driven by the real [LoginFlow] against a fake Nextcloud behind
 * MockWebServer: what the user sees at each step and for each error. Real time, short intervals.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalDavLoginViewModelServerTest {
    @StartStop
    val server = MockWebServer()

    private val nextcloud by lazy { FakeNextcloud(server).also { server.dispatcher = it } }
    private val session = AccountSession(CredentialStore(FakeLoginStorage(), FakeCipher()))
    private val real = LoginFlow(LoginFlowApiFactory(OkHttpClient(), NextcloudJson), session)

    // Real time, like the flow's own tests: virtual time would never fire the login timeout.
    @BeforeEach
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /** The real flow against the local plain-http server, as the screen's flow would be. */
    private fun model(timeoutMs: Long = 2_000): CalDavLoginViewModel {
        nextcloud
        val url = (
            ServerUrl.parse(server.url("/nextcloud/").toString(), allowInsecure = true) as
                ServerUrl.ParseResult.Valid
            ).url
        val flow = mockk<LoginFlow>()
        every { flow.login(any()) } answers
            { real.login(url, 10.milliseconds, timeoutMs.milliseconds) }
        return CalDavLoginViewModel(flow)
    }

    /** Waits until the flow is over: a failure, or the form cleared after signing in. */
    private suspend fun CalDavLoginViewModel.finalState() = state.first { it.error != null }

    @Test
    fun `a good login shows the wait, then signs in and clears the form`() = runBlocking {
        val model = model()
        model.onServerChange("cloud.example.com")
        val steps = mutableListOf<LoginState>()
        val collecting = launch(Dispatchers.Unconfined) {
            model.state.collect { state -> state.step?.let(steps::add) }
        }

        model.connect()
        withTimeout(WAIT_MS) { session.activeAccount.first { it != null } }
        // Signed in: the form is empty again, so a later sign-out starts clean.
        withTimeout(WAIT_MS) { model.state.first { it.server.isEmpty() } }
        collecting.cancel()

        assertEquals(LoginState.CheckingServer, steps.first())
        assertEquals(true, steps.any { it is LoginState.WaitingForBrowser })
        assertEquals("username", session.activeAccount.value?.loginName)
    }

    @Test
    fun `a server that is not Nextcloud is reported`() = runBlocking {
        nextcloud.statusBody = """{"installed": false}"""
        val model = model()
        model.onServerChange("cloud.example.com")

        model.connect()

        assertEquals(LoginError.NOT_NEXTCLOUD, model.finalState().error)
        assertNull(session.activeAccount.value)
    }

    @Test
    fun `an unreachable server is reported`() = runBlocking {
        val model = model()
        model.onServerChange("cloud.example.com")
        server.close()

        model.connect()

        assertEquals(LoginError.UNREACHABLE, model.finalState().error)
    }

    @Test
    fun `waiting too long expires the login`() = runBlocking {
        nextcloud.pollsBeforeLogin = Int.MAX_VALUE
        val model = model(timeoutMs = 300)
        model.onServerChange("cloud.example.com")

        model.connect()

        assertEquals(LoginError.EXPIRED, model.finalState().error)
        assertNull(session.activeAccount.value)
    }

    @Test
    fun `cancelling while waiting leaves nobody signed in`() = runBlocking {
        nextcloud.pollsBeforeLogin = Int.MAX_VALUE
        val model = model()
        model.onServerChange("cloud.example.com")
        model.connect()
        withTimeout(WAIT_MS) { model.state.first { it.loginUrl != null } }

        model.cancel()

        assertNull(model.state.value.step)
        assertNull(session.activeAccount.value)
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
