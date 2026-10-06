// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.auth

import com.qtekfun.ultimatecalendar.data.remote.ApiResult
import com.qtekfun.ultimatecalendar.data.remote.Credentials
import com.qtekfun.ultimatecalendar.data.remote.NextcloudJson
import com.qtekfun.ultimatecalendar.data.remote.ServerUrl
import com.qtekfun.ultimatecalendar.data.remote.apiCall
import com.qtekfun.ultimatecalendar.data.remote.json
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Reads a fixture of src/test/resources/login. */
fun loginFixture(name: String): String =
    checkNotNull(LoginFlowApiTest::class.java.classLoader?.getResourceAsStream("login/$name")).use {
        it.readBytes().toString(Charsets.UTF_8)
    }

/** A [LoginFlowApi] against a local plain-http server, allowed only in tests. */
fun testLoginApi(server: MockWebServer, client: OkHttpClient = OkHttpClient()): LoginFlowApi {
    val url = ServerUrl.parse(
        server.url("/nextcloud/").toString(),
        allowInsecure = true
    ) as ServerUrl.ParseResult.Valid
    return LoginFlowApiFactory(client, NextcloudJson).create(url.url)
}

class LoginFlowApiTest {
    @StartStop
    val server = MockWebServer()

    private val api by lazy { testLoginApi(server) }
    private val auth = basicAuth(Credentials("ana", "app-password"))

    @Test
    fun `reads the server status`() = runTest {
        server.enqueue(json(loginFixture("status.json")))

        val status = (apiCall { api.status() } as ApiResult.Success).value

        assertEquals(true, status.installed)
        assertEquals("35.0.1.2", status.version)
        assertEquals("/nextcloud/status.php", server.takeRequest().target)
    }

    @Test
    fun `starts the login flow with an empty POST`() = runTest {
        server.enqueue(json(loginFixture("login_start.json")))

        val start = (apiCall { api.startLogin() } as ApiResult.Success).value

        assertEquals("https://cloud.example.com/login/v2/poll", start.poll.endpoint)
        assertEquals(true, start.login.startsWith("https://cloud.example.com/login/v2/flow/"))
        val request = server.takeRequest()
        assertEquals("POST /nextcloud/index.php/login/v2", "${request.method} ${request.target}")
    }

    @Test
    fun `polls the endpoint given by the server with the token as a form`() = runTest {
        server.enqueue(MockResponse(404))
        server.enqueue(json(loginFixture("login_result.json")))
        val endpoint = server.url("/nextcloud/login/v2/poll").toString()

        val pending = apiCall { api.poll(endpoint, "tok/en+1") }
        val done = apiCall { api.poll(endpoint, "tok/en+1") }

        assertEquals(ApiResult.NotFound, pending)
        assertEquals("username", (done as ApiResult.Success).value.loginName)
        val request = server.takeRequest()
        assertEquals("/nextcloud/login/v2/poll", request.target)
        assertEquals("token=tok%2Fen%2B1", request.body?.utf8())
    }

    @Test
    fun `revokes the app password`() = runTest {
        server.enqueue(MockResponse(200))

        val result = apiCall { api.revokeAppPassword(auth) }

        assertEquals(ApiResult.Success(Unit), result)
        val request = server.takeRequest()
        assertEquals(
            "DELETE /nextcloud/ocs/v2.php/core/apppassword",
            "${request.method} ${request.target}"
        )
        assertEquals(auth, request.headers["Authorization"])
        assertEquals("true", request.headers["OCS-APIRequest"])
    }
}
