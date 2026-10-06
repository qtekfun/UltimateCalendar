// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote

import com.qtekfun.ultimatecalendar.data.auth.loginFixture
import com.qtekfun.ultimatecalendar.data.auth.testLoginApi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import mockwebserver3.junit5.StartStop
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ApiCallTest {
    @StartStop
    val server = MockWebServer()

    /** Short timeouts so the timeout test is fast. */
    private val client = OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build()
    private val api by lazy { testLoginApi(server, client) }

    private suspend fun status() = apiCall { api.status() }

    @Test
    fun `returns the parsed body`() = runTest {
        server.enqueue(json(loginFixture("status.json")))

        val result = status()

        assertInstanceOf(ApiResult.Success::class.java, result)
        assertEquals("Nextcloud", (result as ApiResult.Success).value.productname)
    }

    @ParameterizedTest
    @CsvSource("401, Unauthorized", "404, NotFound")
    fun `maps auth and missing errors to their own results`(code: Int, expected: String) = runTest {
        server.enqueue(json("{}", code))

        assertEquals(expected, status()::class.simpleName)
    }

    @ParameterizedTest
    @CsvSource("400", "403", "500", "503")
    fun `maps other client and server errors to HttpError`(code: Int) = runTest {
        server.enqueue(json("{\"message\":\"nope\"}", code))

        assertEquals(ApiResult.HttpError(code), status())
    }

    @Test
    fun `maps a slow server to a timeout`() = runTest {
        server.enqueue(json("{}").newBuilder().headersDelay(2, TimeUnit.SECONDS).build())

        assertEquals(ApiResult.NetworkError(ApiResult.NetworkError.Kind.TIMEOUT), status())
    }

    @Test
    fun `maps a server that is down to unreachable`() = runTest {
        val apiOfClosedServer = testLoginApi(server, client)
        server.close()

        assertEquals(
            ApiResult.NetworkError(ApiResult.NetworkError.Kind.UNREACHABLE),
            apiCall { apiOfClosedServer.status() }
        )
    }

    @Test
    fun `maps a connection dropped mid-response to a network error`() = runTest {
        server.enqueue(json("{}").newBuilder().onResponseStart(SocketEffect.CloseSocket()).build())

        assertInstanceOf(ApiResult.NetworkError::class.java, status())
    }

    @Test
    fun `maps unexpected JSON to a parse error`() = runTest {
        server.enqueue(json("[\"not\", \"an object\"]"))

        assertEquals(ApiResult.ParseError, status())
    }

    @Test
    fun `maps a successful empty body to a parse error when content was expected`() = runTest {
        server.enqueue(MockResponse(204))

        assertEquals(ApiResult.ParseError, status())
    }
}
