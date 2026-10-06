// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote.caldav

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DavHttpTest {
    private fun failingWith(exception: IOException) = DavHttp(
        OkHttpClient.Builder().addInterceptor { throw exception }.build(),
        "https://cloud.example.com/".toHttpUrl(),
        Dispatchers.Unconfined
    )

    @Test
    fun `network failures become their kind`() = runTest {
        mapOf(
            SocketTimeoutException() to DavResult.NetworkError.Kind.TIMEOUT,
            UnknownHostException() to DavResult.NetworkError.Kind.UNREACHABLE,
            ConnectException() to DavResult.NetworkError.Kind.UNREACHABLE,
            NoRouteToHostException() to DavResult.NetworkError.Kind.UNREACHABLE,
            SSLHandshakeException("bad certificate") to DavResult.NetworkError.Kind.TLS,
            IOException() to DavResult.NetworkError.Kind.OTHER
        ).forEach { (exception, kind) ->
            assertEquals(DavResult.NetworkError(kind), failingWith(exception).send("GET", "/x"))
        }
    }

    @Test
    fun `failures pass through map and then untouched`() {
        val failures = listOf(
            DavResult.Unauthorized,
            DavResult.Forbidden,
            DavResult.NotFound,
            DavResult.PreconditionFailed,
            DavResult.SyncTokenExpired,
            DavResult.HttpError(500),
            DavResult.NetworkError(DavResult.NetworkError.Kind.OTHER),
            DavResult.ParseError
        )
        failures.forEach { failure ->
            assertEquals(failure, failure.map { 1 })
            assertEquals(failure, failure.then { DavResult.Success(1) })
        }
    }
}
