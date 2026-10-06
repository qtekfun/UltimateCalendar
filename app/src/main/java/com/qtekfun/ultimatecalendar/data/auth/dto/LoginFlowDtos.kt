// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.auth.dto

import kotlinx.serialization.Serializable

/** `status.php`: present on every Nextcloud server, no login needed. */
@Serializable
data class StatusDto(
    val installed: Boolean = false,
    val maintenance: Boolean = false,
    val version: String? = null,
    val productname: String? = null
)

/** Response of POST index.php/login/v2. */
@Serializable
data class LoginStartDto(val poll: Poll, val login: String) {
    @Serializable
    data class Poll(val token: String, val endpoint: String)
}

/** Response of the poll endpoint once the user has logged in. Returned only once. */
@Serializable
data class LoginResultDto(val server: String, val loginName: String, val appPassword: String)
