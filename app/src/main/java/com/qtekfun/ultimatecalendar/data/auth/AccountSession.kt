// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.auth

import com.qtekfun.ultimatecalendar.data.remote.Credentials
import com.qtekfun.ultimatecalendar.data.remote.CredentialsProvider
import com.qtekfun.ultimatecalendar.data.remote.ServerUrl
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Who is signed in to the built-in CalDAV: the server and the login name. No secrets. */
data class SignedInAccount(val serverUrl: String, val loginName: String)

/**
 * The signed-in CalDAV account (a single one, RF-12). Decrypted credentials are kept only in
 * memory and handed to the API client; the persisted copy is encrypted by [CredentialStore].
 */
@Singleton
class AccountSession @Inject constructor(private val credentialStore: CredentialStore) :
    CredentialsProvider {
    private val account = MutableStateFlow<SignedInAccount?>(null)

    @Volatile
    private var current: Credentials? = null

    val activeAccount: StateFlow<SignedInAccount?> = account.asStateFlow()

    /** Loads the stored login, e.g. when the app starts. */
    fun restore(): SignedInAccount? {
        val saved = credentialStore.load()
        current = saved?.credentials
        val restored = saved?.let { SignedInAccount(it.serverUrl, it.credentials.loginName) }
        account.value = restored
        return restored
    }

    /** Stores the encrypted credentials and makes the account the active one. */
    fun signIn(server: ServerUrl, credentials: Credentials): SignedInAccount {
        credentialStore.save(server.root.toString(), credentials)
        current = credentials
        val signedIn = SignedInAccount(server.root.toString(), credentials.loginName)
        account.value = signedIn
        return signedIn
    }

    /** Forgets the account and deletes its stored credentials. */
    fun signOut() {
        credentialStore.clear()
        current = null
        account.value = null
    }

    override fun credentials(): Credentials? = current
}
