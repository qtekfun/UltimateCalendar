// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.auth

/** In-memory [LoginStorage]. [rawText] is everything that would hit the disk, as text. */
class FakeLoginStorage : LoginStorage {
    private var login: StoredLogin? = null

    override fun read(): StoredLogin? = login

    override fun write(login: StoredLogin) {
        this.login = login
    }

    override fun clear() {
        login = null
    }

    fun rawText(): String = login?.let {
        it.serverUrl + it.loginName + it.secret.ciphertext.toString(Charsets.ISO_8859_1)
    }.orEmpty()
}
