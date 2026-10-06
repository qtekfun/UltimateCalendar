// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.auth

import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount

/** Steps of the login (RF-12), as shown to the user. */
sealed interface LoginState {
    data object CheckingServer : LoginState

    /** The user must log in at [loginUrl] in the browser; the app polls meanwhile. */
    data class WaitingForBrowser(val loginUrl: String) : LoginState

    data class LoggedIn(val account: SignedInAccount) : LoginState

    data class Failed(val error: LoginError) : LoginState
}

/** Why a login failed, each with its own clear message in the UI. */
enum class LoginError {
    INVALID_URL,
    INSECURE_URL,
    NOT_NEXTCLOUD,
    UNREACHABLE,
    TLS_ERROR,
    EXPIRED,
    UNKNOWN
}
