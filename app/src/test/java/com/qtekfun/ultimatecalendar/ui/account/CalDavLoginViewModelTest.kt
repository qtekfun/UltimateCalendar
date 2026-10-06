// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.account

import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.CredentialStore
import com.qtekfun.ultimatecalendar.data.auth.FakeCipher
import com.qtekfun.ultimatecalendar.data.auth.FakeLoginStorage
import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.domain.auth.LoginError
import com.qtekfun.ultimatecalendar.domain.auth.LoginFlow
import com.qtekfun.ultimatecalendar.domain.auth.LoginState
import com.qtekfun.ultimatecalendar.domain.auth.LoginUiState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The login's states as the screen sees them, with a scripted [LoginFlow]. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalDavLoginViewModelTest {
    private val account = SignedInAccount("https://cloud.example.com/", "ana")
    private val flow = mockk<LoginFlow>()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun model() = CalDavLoginViewModel(flow)

    @Test
    fun `typing keeps the address and warns about plain http at once`() = runTest {
        val model = model()

        model.onServerChange("http://cloud.example.com")

        assertEquals(LoginError.INSECURE_URL, model.state.value.error)
        model.onServerChange("cloud.example.com")
        assertEquals(null, model.state.value.error)
        assertEquals("cloud.example.com", model.state.value.server)
    }

    @Test
    fun `connecting goes through checking and waiting, then hands over to the account`() = runTest {
        val gate = CompletableDeferred<Unit>()
        every { flow.login("cloud.example.com") } returns flow {
            emit(LoginState.CheckingServer)
            emit(LoginState.WaitingForBrowser("https://cloud.example.com/login/v2/flow/abc"))
            gate.await()
            emit(LoginState.LoggedIn(account))
        }
        val model = model()
        model.onServerChange("cloud.example.com")

        model.state.test {
            assertEquals(LoginUiState("cloud.example.com"), awaitItem())
            model.connect()
            // Checking and waiting follow at once; a slow reader may see only the latter.
            var waiting = awaitItem()
            while (waiting.loginUrl == null) waiting = awaitItem()
            assertEquals("https://cloud.example.com/login/v2/flow/abc", waiting.loginUrl)
            assertEquals(true, waiting.busy)

            gate.complete(Unit)
            // Signed in: the form is empty again for a later sign-out.
            assertEquals(LoginUiState(), awaitItem())
        }
    }

    @Test
    fun `every failure is shown and the address is kept for another try`() = runTest {
        LoginError.entries.forEach { error ->
            every { flow.login("cloud.example.com") } returns flowOf(LoginState.Failed(error))
            val model = model()
            model.onServerChange("cloud.example.com")

            model.connect()

            assertEquals(error, model.state.value.error)
            assertEquals("cloud.example.com", model.state.value.server)
            assertEquals(true, model.state.value.canSubmit)
        }
    }

    @Test
    fun `editing the address after a failure clears it`() = runTest {
        every { flow.login("clud") } returns flowOf(LoginState.Failed(LoginError.UNREACHABLE))
        val model = model()
        model.onServerChange("clud")
        model.connect()

        model.onServerChange("cloud.example.com")

        assertEquals(LoginUiState("cloud.example.com"), model.state.value)
    }

    @Test
    fun `cancelling stops the flow and goes back to the form`() = runTest {
        val states = MutableSharedFlow<LoginState>()
        every { flow.login("cloud.example.com") } returns states
        val model = model()
        model.onServerChange("cloud.example.com")
        model.connect()
        states.emit(LoginState.WaitingForBrowser("https://cloud.example.com/login/v2/flow/abc"))
        assertEquals(true, model.state.value.busy)

        model.cancel()
        // A late answer from the cancelled flow changes nothing.
        states.emit(LoginState.Failed(LoginError.EXPIRED))

        assertEquals(LoginUiState("cloud.example.com"), model.state.value)
        assertEquals(0, states.subscriptionCount.value)
    }

    @Test
    fun `an empty address does not start the flow`() = runTest {
        val model = model()

        model.connect()

        verify(exactly = 0) { flow.login(any()) }
        assertEquals(LoginUiState(), model.state.value)
    }

    @Test
    fun `connecting again after a cancel starts a fresh flow`() = runTest {
        val first = MutableSharedFlow<LoginState>()
        every { flow.login("a.example.com") } returns first
        every { flow.login("b.example.com") } returns
            flowOf(LoginState.Failed(LoginError.TLS_ERROR))
        val model = model()
        model.onServerChange("a.example.com")
        model.connect()
        // The field is off while busy, so the address changes only after a failure or a cancel.
        model.cancel()
        model.onServerChange("b.example.com")
        model.connect()

        assertEquals(LoginError.TLS_ERROR, model.state.value.error)
        assertEquals(0, first.subscriptionCount.value)
    }

    @Test
    fun `the real flow refuses plain http and nonsense before any request`() = runTest {
        // The flow itself, with no network: it fails on the address alone.
        val real = LoginFlow(
            mockk(),
            AccountSession(CredentialStore(FakeLoginStorage(), FakeCipher()))
        )
        val model = CalDavLoginViewModel(real)

        model.onServerChange("http://cloud.example.com")
        model.connect()
        assertEquals(LoginError.INSECURE_URL, model.state.value.error)

        model.onServerChange("https://")
        model.connect()
        assertEquals(LoginError.INVALID_URL, model.state.value.error)
    }
}
