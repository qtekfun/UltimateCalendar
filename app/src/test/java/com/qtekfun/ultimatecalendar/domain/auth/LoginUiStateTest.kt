// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.auth

import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoginUiStateTest {
    @Test
    fun `the address is normalized to the server root over https`() {
        assertEquals(
            AddressCheck.Valid("https://cloud.example.com/"),
            ServerAddress.check("cloud.example.com")
        )
        assertEquals(
            AddressCheck.Valid("https://example.com/nextcloud/"),
            ServerAddress.check("  https://example.com/nextcloud/index.php/apps/files?dir=/  ")
        )
    }

    @Test
    fun `blank, plain http and nonsense are told apart`() {
        assertEquals(AddressCheck.Empty, ServerAddress.check("   "))
        assertEquals(AddressCheck.Insecure, ServerAddress.check("http://cloud.example.com"))
        assertEquals(AddressCheck.Invalid, ServerAddress.check("https://"))
    }

    @Test
    fun `the button works for any text but not while busy`() {
        assertFalse(LoginUiState("").canSubmit)
        assertFalse(LoginUiState("  ").canSubmit)
        assertTrue(LoginUiState("not a url").canSubmit)
        assertFalse(LoginUiState("cloud.example.com", LoginState.CheckingServer).canSubmit)
        assertTrue(
            LoginUiState("cloud.example.com", LoginState.Failed(LoginError.UNREACHABLE)).canSubmit
        )
    }

    @Test
    fun `busy while checking or waiting, not before, after a failure or when signed in`() {
        val account = SignedInAccount("https://cloud.example.com/", "ana")

        assertFalse(LoginUiState("x").busy)
        assertTrue(LoginUiState("x", LoginState.CheckingServer).busy)
        assertTrue(LoginUiState("x", LoginState.WaitingForBrowser("https://b")).busy)
        assertFalse(LoginUiState("x", LoginState.Failed(LoginError.EXPIRED)).busy)
        assertFalse(LoginUiState("x", LoginState.LoggedIn(account)).busy)
    }

    @Test
    fun `plain http is flagged while typing, a failure when it came back`() {
        assertEquals(
            LoginError.INSECURE_URL,
            LoginUiState("http://cloud.example.com").error
        )
        assertNull(LoginUiState("cloud.example.com").error)
        // A malformed address is explained by the flow when tried, not while typing.
        assertNull(LoginUiState("https://").error)
        assertEquals(
            LoginError.TLS_ERROR,
            LoginUiState("cloud.example.com", LoginState.Failed(LoginError.TLS_ERROR)).error
        )
    }

    @Test
    fun `shows where it will connect only for a good address`() {
        assertEquals("https://cloud.example.com/", LoginUiState("cloud.example.com").target)
        assertNull(LoginUiState("http://cloud.example.com").target)
        assertNull(LoginUiState("").target)
    }

    @Test
    fun `the browser address is there only while waiting`() {
        assertEquals(
            "https://cloud.example.com/login/v2/flow/abc",
            LoginUiState(
                "x",
                LoginState.WaitingForBrowser("https://cloud.example.com/login/v2/flow/abc")
            ).loginUrl
        )
        assertNull(LoginUiState("x", LoginState.CheckingServer).loginUrl)
    }

    @Test
    fun `editing the address clears a failure but not a flow under way`() {
        val failed = LoginUiState("cloud", LoginState.Failed(LoginError.UNREACHABLE))
        assertEquals(LoginUiState("cloud.example.com"), failed.withServer("cloud.example.com"))

        val waiting = LoginUiState("cloud", LoginState.CheckingServer)
        assertEquals(LoginState.CheckingServer, waiting.withServer("cloud2").step)
    }

    @Test
    fun `cancelling goes back to the form with the address kept`() {
        val waiting = LoginUiState("cloud.example.com", LoginState.WaitingForBrowser("https://b"))

        assertEquals(LoginUiState("cloud.example.com"), waiting.cancelled())
        assertEquals(
            LoginState.CheckingServer,
            LoginUiState("x").withStep(LoginState.CheckingServer).step
        )
    }
}
