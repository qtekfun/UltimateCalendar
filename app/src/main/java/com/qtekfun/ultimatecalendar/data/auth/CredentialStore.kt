// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.auth

import com.qtekfun.ultimatecalendar.data.remote.Credentials
import java.security.GeneralSecurityException
import javax.inject.Inject

/** A sign-in that can be restored: the server address and its credentials. */
class SavedLogin(val serverUrl: String, val credentials: Credentials)

/**
 * Stores the app password encrypted at rest; plaintext only ever exists in memory. If the
 * Keystore key is gone (backup restore, lock screen reset) the stored login is useless: it is
 * deleted and the user signs in again.
 */
class CredentialStore @Inject constructor(
    private val storage: LoginStorage,
    private val cipher: SecretCipher
) {
    fun save(serverUrl: String, credentials: Credentials) {
        val secret = cipher.encrypt(credentials.appPassword.toByteArray(Charsets.UTF_8))
        storage.write(StoredLogin(serverUrl, credentials.loginName, secret))
    }

    fun load(): SavedLogin? {
        val stored = storage.read() ?: return null
        return try {
            val password = cipher.decrypt(stored.secret).toString(Charsets.UTF_8)
            SavedLogin(stored.serverUrl, Credentials(stored.loginName, password))
        } catch (_: GeneralSecurityException) {
            storage.clear()
            null
        }
    }

    fun clear() = storage.clear()
}
