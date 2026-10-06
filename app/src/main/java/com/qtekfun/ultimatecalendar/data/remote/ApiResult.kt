// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote

/** Outcome of a Nextcloud API call. Network and HTTP failures are values, never exceptions. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    /** 401: the app password is wrong or was revoked. */
    data object Unauthorized : ApiResult<Nothing>

    /** 404: the resource does not exist (or, while polling the login, not yet). */
    data object NotFound : ApiResult<Nothing>

    /** Any other 4xx or 5xx response. */
    data class HttpError(val code: Int) : ApiResult<Nothing>

    data class NetworkError(val kind: Kind) : ApiResult<Nothing> {
        enum class Kind { TIMEOUT, UNREACHABLE, TLS, OTHER }
    }

    /** The server answered something that is not the expected JSON. */
    data object ParseError : ApiResult<Nothing>
}
