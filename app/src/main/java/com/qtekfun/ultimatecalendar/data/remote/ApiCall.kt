// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException
import retrofit2.Response

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404

/**
 * Runs a Retrofit call and turns its response or failure into an [ApiResult]. Coroutine
 * cancellation is not caught, so it still propagates.
 */
suspend fun <T> apiCall(call: suspend () -> Response<T>): ApiResult<T> = try {
    toResult(call())
} catch (_: SocketTimeoutException) {
    ApiResult.NetworkError(ApiResult.NetworkError.Kind.TIMEOUT)
} catch (_: UnknownHostException) {
    ApiResult.NetworkError(ApiResult.NetworkError.Kind.UNREACHABLE)
} catch (_: ConnectException) {
    ApiResult.NetworkError(ApiResult.NetworkError.Kind.UNREACHABLE)
} catch (_: NoRouteToHostException) {
    ApiResult.NetworkError(ApiResult.NetworkError.Kind.UNREACHABLE)
} catch (_: SSLException) {
    ApiResult.NetworkError(ApiResult.NetworkError.Kind.TLS)
} catch (_: SerializationException) {
    ApiResult.ParseError
} catch (_: IOException) {
    ApiResult.NetworkError(ApiResult.NetworkError.Kind.OTHER)
}

private fun <T> toResult(response: Response<T>): ApiResult<T> {
    val body = response.body()
    return when {
        response.code() == HTTP_UNAUTHORIZED -> ApiResult.Unauthorized
        response.code() == HTTP_NOT_FOUND -> ApiResult.NotFound
        !response.isSuccessful -> ApiResult.HttpError(response.code())
        body == null -> ApiResult.ParseError
        else -> ApiResult.Success(body)
    }
}
