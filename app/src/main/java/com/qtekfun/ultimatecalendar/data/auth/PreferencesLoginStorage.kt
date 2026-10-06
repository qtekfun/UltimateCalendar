// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.auth

import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Base64
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * [LoginStorage] in its own SharedPreferences file, apart from the settings. Only the ciphertext
 * of the app password is written. Nothing stored here is ever logged.
 */
@Singleton
class PreferencesLoginStorage @Inject constructor(
    @Named(FILE) private val preferences: SharedPreferences
) : LoginStorage {
    override fun read(): StoredLogin? {
        val server = preferences.getString(SERVER, null)
        val login = preferences.getString(LOGIN_NAME, null)
        val ciphertext = preferences.getString(CIPHERTEXT, null)?.let(::decode)
        val iv = preferences.getString(IV, null)?.let(::decode)
        val secret = if (ciphertext != null && iv != null) EncryptedSecret(ciphertext, iv) else null
        return if (server != null && login != null && secret != null) {
            StoredLogin(server, login, secret)
        } else {
            null
        }
    }

    override fun write(login: StoredLogin) = preferences.edit {
        putString(SERVER, login.serverUrl)
        putString(LOGIN_NAME, login.loginName)
        putString(CIPHERTEXT, Base64.getEncoder().encodeToString(login.secret.ciphertext))
        putString(IV, Base64.getEncoder().encodeToString(login.secret.iv))
    }

    override fun clear() = preferences.edit {
        remove(SERVER)
        remove(LOGIN_NAME)
        remove(CIPHERTEXT)
        remove(IV)
    }

    private fun decode(text: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(text) }.getOrNull()

    companion object {
        const val FILE = "caldav_login"
        private const val SERVER = "server"
        private const val LOGIN_NAME = "login_name"
        private const val CIPHERTEXT = "ciphertext"
        private const val IV = "iv"
    }
}
