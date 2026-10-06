// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.auth

import com.qtekfun.ultimatecalendar.data.auth.loginFixture
import com.qtekfun.ultimatecalendar.data.remote.json
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

/**
 * A scripted Nextcloud for login tests: answers status, login start, poll (404 until
 * [pollsBeforeLogin] polls happened) and app password revocation.
 */
class FakeNextcloud(private val server: MockWebServer) : Dispatcher() {
    var statusBody: String = loginFixture("status.json")
    var statusCode: Int = 200
    var startCode: Int = 200
    var pollsBeforeLogin: Int = 1
    val requests: MutableList<String> = CopyOnWriteArrayList()
    private val polls = AtomicInteger()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        requests += "${request.method} $path"
        return when {
            path.endsWith("/status.php") -> json(statusBody, statusCode)

            path.endsWith("/index.php/login/v2") -> json(startBody(), startCode)

            path.endsWith("/login/v2/poll") ->
                if (polls.incrementAndGet() >
                    pollsBeforeLogin
                ) {
                    json(loginFixture("login_result.json"))
                } else {
                    MockResponse(404)
                }

            path.endsWith("/core/apppassword") -> MockResponse(200)

            else -> MockResponse(404)
        }
    }

    private fun startBody(): String {
        val poll = server.url("/nextcloud/login/v2/poll")
        val login = server.url("/nextcloud/login/v2/flow/abc")
        return """{"poll":{"token":"t0k3n","endpoint":"$poll"},"login":"$login"}"""
    }
}
