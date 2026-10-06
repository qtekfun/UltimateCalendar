// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.auth

/** A login as persisted: the app password is only there encrypted ([secret]). */
class StoredLogin(val serverUrl: String, val loginName: String, val secret: EncryptedSecret)

/** Where the encrypted login lives. It is never part of a backup (`allowBackup=false`). */
interface LoginStorage {
    fun read(): StoredLogin?

    fun write(login: StoredLogin)

    fun clear()
}
