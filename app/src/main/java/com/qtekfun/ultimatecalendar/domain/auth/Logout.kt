// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.auth

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.LoginFlowApiFactory
import com.qtekfun.ultimatecalendar.data.auth.basicAuth
import com.qtekfun.ultimatecalendar.data.remote.ServerUrl
import com.qtekfun.ultimatecalendar.data.remote.apiCall
import javax.inject.Inject

/**
 * Logs out (RF-12): revokes the app password on the server, then forgets the account and its
 * stored credentials. Revocation is best effort: as Nextcloud recommends, the account is removed
 * even if the server cannot be reached. Deleting the local copy of the events is up to the
 * caller, because it lives with the local store.
 */
class Logout @Inject constructor(
    private val apiFactory: LoginFlowApiFactory,
    private val session: AccountSession
) {
    suspend operator fun invoke() = run(allowInsecure = false)

    /** [allowInsecure] exists only so tests can revoke against a local plain-http server. */
    internal suspend fun run(allowInsecure: Boolean) {
        val account = session.activeAccount.value ?: session.restore() ?: return
        val credentials = session.credentials()
        val server = ServerUrl.parse(
            account.serverUrl,
            allowInsecure
        ) as? ServerUrl.ParseResult.Valid
        if (credentials != null && server != null) {
            apiCall { apiFactory.create(server.url).revokeAppPassword(basicAuth(credentials)) }
        }
        session.signOut()
    }
}
