// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.auth

import com.qtekfun.ultimatecalendar.data.remote.ServerUrl

/** What the server address the user typed amounts to. */
sealed interface AddressCheck {
    data object Empty : AddressCheck

    /** Good: this is the address the app will connect to, normalized (https, server root). */
    data class Valid(val normalized: String) : AddressCheck

    /** Plain http: refused, the connection must be HTTPS (SPEC §6). */
    data object Insecure : AddressCheck

    data object Invalid : AddressCheck
}

/** Validates and normalizes the address field of the login (RF-12). */
object ServerAddress {
    fun check(input: String): AddressCheck = if (input.isBlank()) {
        AddressCheck.Empty
    } else {
        when (val parsed = ServerUrl.parse(input)) {
            is ServerUrl.ParseResult.Valid -> AddressCheck.Valid(parsed.url.toString())
            ServerUrl.ParseResult.Insecure -> AddressCheck.Insecure
            ServerUrl.ParseResult.Invalid -> AddressCheck.Invalid
        }
    }
}

/**
 * What the login screen shows: the address field and the step of the flow ([step] is null before
 * the user starts). All the rules of the screen are here, so the screen only draws them.
 */
data class LoginUiState(val server: String = "", val step: LoginState? = null) {
    /** A check or the wait for the browser is under way: the field and the button are off. */
    val busy: Boolean
        get() = when (step) {
            null, is LoginState.Failed, is LoginState.LoggedIn -> false
            LoginState.CheckingServer, is LoginState.WaitingForBrowser -> true
        }

    val address: AddressCheck get() = ServerAddress.check(server)

    /** The button works for any text: a malformed address is explained when it is tried. */
    val canSubmit: Boolean get() = !busy && server.isNotBlank()

    /**
     * What to say about the address: the reason the flow failed, or, while typing, that plain http
     * will not do (that one is certain, so it is said before the user tries).
     */
    val error: LoginError?
        get() = (step as? LoginState.Failed)?.error
            ?: LoginError.INSECURE_URL.takeIf { address == AddressCheck.Insecure }

    /** Where the app will connect, once the address is good and nothing failed. */
    val target: String? get() = (address as? AddressCheck.Valid)?.normalized

    /** The browser page the user must complete, while the app waits for it. */
    val loginUrl: String? get() = (step as? LoginState.WaitingForBrowser)?.loginUrl

    /** Editing the address clears an earlier failure; what is typed is kept as is. */
    fun withServer(text: String): LoginUiState =
        copy(server = text, step = if (step is LoginState.Failed) null else step)

    fun withStep(next: LoginState): LoginUiState = copy(step = next)

    /** Stops waiting; the token simply expires on the server. */
    fun cancelled(): LoginUiState = copy(step = null)
}
