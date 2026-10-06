// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote.caldav

/** Outcome of a CalDAV request. Network and HTTP failures are values, never exceptions. */
sealed interface DavResult<out T> {
    data class Success<T>(val value: T) : DavResult<T>

    /** 401: the app password is wrong or was revoked. */
    data object Unauthorized : DavResult<Nothing>

    /** 403: the user may not do this, such as writing to a calendar shared read-only. */
    data object Forbidden : DavResult<Nothing>

    /** 404: the resource or calendar no longer exists on the server. */
    data object NotFound : DavResult<Nothing>

    /** 412: the ETag sent is no longer current, or a new resource already exists (conflict). */
    data object PreconditionFailed : DavResult<Nothing>

    /** The sync token is no longer valid: pull the whole calendar again (RFC 6578 §3.2). */
    data object SyncTokenExpired : DavResult<Nothing>

    /** Any other 4xx or 5xx response. */
    data class HttpError(val code: Int) : DavResult<Nothing>

    data class NetworkError(val kind: Kind) : DavResult<Nothing> {
        enum class Kind { TIMEOUT, UNREACHABLE, TLS, OTHER }
    }

    /** The server answered something that is not the expected XML. */
    data object ParseError : DavResult<Nothing>
}

/** Transforms a successful value; failures pass through unchanged. */
inline fun <T, R> DavResult<T>.map(transform: (T) -> R): DavResult<R> = when (this) {
    is DavResult.Success -> DavResult.Success(transform(value))
    DavResult.Unauthorized -> DavResult.Unauthorized
    DavResult.Forbidden -> DavResult.Forbidden
    DavResult.NotFound -> DavResult.NotFound
    DavResult.PreconditionFailed -> DavResult.PreconditionFailed
    DavResult.SyncTokenExpired -> DavResult.SyncTokenExpired
    is DavResult.HttpError -> this
    is DavResult.NetworkError -> this
    DavResult.ParseError -> DavResult.ParseError
}

/** Chains another request after a successful one. */
inline fun <T, R> DavResult<T>.then(next: (T) -> DavResult<R>): DavResult<R> = when (this) {
    is DavResult.Success -> next(value)
    else -> map { error("unreachable") }
}
