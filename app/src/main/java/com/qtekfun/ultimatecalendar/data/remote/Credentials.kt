// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote

/** Login name and app password of an account (RF-12). The password never shows in [toString]. */
class Credentials(val loginName: String, val appPassword: String) {
    override fun toString(): String = "Credentials(loginName=$loginName, appPassword=***)"
}

/** Where network clients get the current credentials, decrypted from the Keystore. */
fun interface CredentialsProvider {
    fun credentials(): Credentials?
}
