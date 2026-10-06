// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import com.qtekfun.ultimatecalendar.domain.subscriptions.SubscriptionError
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.Headers.Companion.headersOf
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionDownloaderTest {
    @StartStop
    val server = MockWebServer()

    /** Talks to MockWebServer, which speaks plain http: only tests allow that. */
    private fun local() = SubscriptionDownloader(OkHttpClient(), Dispatchers.Unconfined, true)

    private fun url(path: String = "/feed.ics") = server.url(path).toString()

    /** The real rules (https only) over answers made up by [answer], with no network at all. */
    private fun offline(answer: (okhttp3.Request) -> Response) = SubscriptionDownloader(
        OkHttpClient.Builder().addInterceptor(Interceptor { answer(it.request()) }).build(),
        Dispatchers.Unconfined
    )

    private fun reply(
        request: okhttp3.Request,
        code: Int,
        body: okhttp3.ResponseBody = "".toResponseBody(),
        vararg headers: String
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("m")
        .headers(headersOf(*headers))
        .body(body)
        .build()

    private fun failing(exception: IOException) =
        offline { throw exception }.let { it to "https://cal.example.com/a.ics" }

    @Test
    fun `a calendar comes with its validators`() = runTest {
        server.enqueue(
            MockResponse(
                200,
                headersOf("ETag", "\"v1\"", "Last-Modified", "Tue, 06 Oct 2026 10:00:00 GMT"),
                "BEGIN:VCALENDAR\nEND:VCALENDAR\n"
            )
        )

        val result = local().fetch(url(), null, null)

        assertEquals(
            FetchResult.Fresh(
                "BEGIN:VCALENDAR\nEND:VCALENDAR\n",
                "\"v1\"",
                "Tue, 06 Oct 2026 10:00:00 GMT"
            ),
            result
        )
        val request = server.takeRequest()
        assertNull(request.headers["If-None-Match"])
        assertNull(request.headers["Authorization"])
        assertTrue(request.headers["Accept"]!!.startsWith("text/calendar"))
    }

    @Test
    fun `a byte order mark is not part of the text`() = runTest {
        server.enqueue(MockResponse(200, headersOf(), "BEGIN:VCALENDAR\n"))

        assertEquals(
            "BEGIN:VCALENDAR\n",
            (local().fetch(url(), null, null) as FetchResult.Fresh).body
        )
    }

    @Test
    fun `the stored validators are sent and a 304 keeps what we have`() = runTest {
        server.enqueue(MockResponse(304))

        val result = local().fetch(url(), "\"v1\"", "Tue, 06 Oct 2026 10:00:00 GMT")

        assertEquals(FetchResult.NotModified, result)
        val request = server.takeRequest()
        assertEquals("\"v1\"", request.headers["If-None-Match"])
        assertEquals("Tue, 06 Oct 2026 10:00:00 GMT", request.headers["If-Modified-Since"])
    }

    @Test
    fun `redirects are followed, relative ones too, and no cookie travels`() = runTest {
        server.enqueue(
            MockResponse(302, headersOf("Location", "/moved/feed.ics", "Set-Cookie", "s=1"), "")
        )
        server.enqueue(MockResponse(200, headersOf(), "BEGIN:VCALENDAR\n"))

        val result = local().fetch(url(), null, null)

        assertEquals("BEGIN:VCALENDAR\n", (result as FetchResult.Fresh).body)
        assertEquals("/feed.ics", server.takeRequest().target)
        val second = server.takeRequest()
        assertEquals("/moved/feed.ics", second.target)
        assertNull(second.headers["Cookie"])
    }

    @Test
    fun `every kind of redirect is followed`() = runTest {
        listOf(301, 302, 303, 307, 308).forEach { code ->
            server.enqueue(MockResponse(code, headersOf("Location", "/there.ics"), ""))
            server.enqueue(MockResponse(200, headersOf(), "ok"))

            assertEquals(
                "ok",
                (local().fetch(url(), null, null) as FetchResult.Fresh).body,
                "$code"
            )
            server.takeRequest()
            server.takeRequest()
        }
    }

    @Test
    fun `an endless chain of redirects stops`() = runTest {
        repeat(SubscriptionDownloader.MAX_REDIRECTS + 1) {
            server.enqueue(MockResponse(302, headersOf("Location", "/again"), ""))
        }

        assertEquals(
            FetchResult.Failed(SubscriptionError.TOO_MANY_REDIRECTS),
            local().fetch(url(), null, null)
        )
        assertEquals(SubscriptionDownloader.MAX_REDIRECTS + 1, server.requestCount)
    }

    @Test
    fun `a redirect to plain http is refused`() = runTest {
        val downloader = offline { request ->
            reply(request, 302, "".toResponseBody(), "Location", "http://cal.example.com/a.ics")
        }

        assertEquals(
            FetchResult.Failed(SubscriptionError.INSECURE_REDIRECT),
            downloader.fetch("https://cal.example.com/a.ics", null, null)
        )
    }

    @Test
    fun `a redirect without a usable place is refused`() = runTest {
        val none = offline { reply(it, 302) }
        val odd = offline { reply(it, 301, "".toResponseBody(), "Location", "ftp://example.com/a") }

        assertEquals(
            FetchResult.Failed(SubscriptionError.INSECURE_REDIRECT),
            none.fetch("https://cal.example.com/a.ics", null, null)
        )
        assertEquals(
            FetchResult.Failed(SubscriptionError.INSECURE_REDIRECT),
            odd.fetch("https://cal.example.com/a.ics", null, null)
        )
    }

    @Test
    fun `plain http and things that are not addresses are refused at once`() = runTest {
        val downloader = offline { error("must not be asked") }

        listOf("http://cal.example.com/a.ics", "not a url").forEach {
            assertEquals(
                FetchResult.Failed(SubscriptionError.INSECURE_REDIRECT),
                downloader.fetch(it, null, null),
                it
            )
        }
    }

    @Test
    fun `error statuses are failures with their code`() = runTest {
        listOf(401, 403, 404, 410, 500, 503).forEach { code ->
            server.enqueue(MockResponse(code))

            assertEquals(
                FetchResult.Failed(SubscriptionError.HTTP, code),
                local().fetch(url(), null, null),
                "$code"
            )
            server.takeRequest()
        }
    }

    @Test
    fun `an empty answer is not a calendar`() = runTest {
        server.enqueue(MockResponse(204))

        assertEquals(
            FetchResult.Failed(SubscriptionError.NOT_CALENDAR),
            local().fetch(url(), null, null)
        )
    }

    @Test
    fun `a feed over the size cap is refused, announced or not`() = runTest {
        val tooBig = "x".repeat((SubscriptionDownloader.MAX_BYTES + 1).toInt())
        server.enqueue(MockResponse(200, headersOf(), tooBig))
        val streamed = offline { request ->
            val source = Buffer().writeUtf8(tooBig)
            reply(request, 200, source.asResponseBody())
        }

        assertEquals(
            FetchResult.Failed(SubscriptionError.TOO_LARGE),
            local().fetch(url(), null, null)
        )
        assertEquals(
            FetchResult.Failed(SubscriptionError.TOO_LARGE),
            streamed.fetch("https://cal.example.com/a.ics", null, null)
        )
    }

    @Test
    fun `a feed of exactly the cap is accepted`() = runTest {
        val exact = "x".repeat(SubscriptionDownloader.MAX_BYTES.toInt())
        server.enqueue(MockResponse(200, headersOf(), exact))

        assertEquals(
            SubscriptionDownloader.MAX_BYTES.toInt(),
            (local().fetch(url(), null, null) as FetchResult.Fresh).body.length
        )
    }

    @Test
    fun `network failures become their kind`() = runTest {
        mapOf(
            SocketTimeoutException() to SubscriptionError.TIMEOUT,
            UnknownHostException() to SubscriptionError.UNREACHABLE,
            ConnectException() to SubscriptionError.UNREACHABLE,
            NoRouteToHostException() to SubscriptionError.UNREACHABLE,
            SSLHandshakeException("bad certificate") to SubscriptionError.TLS,
            IOException() to SubscriptionError.NETWORK
        ).forEach { (exception, error) ->
            val (downloader, address) = failing(exception)

            assertEquals(
                FetchResult.Failed(error),
                downloader.fetch(address, null, null),
                exception.javaClass.simpleName
            )
        }
    }

    @Test
    fun `a server that never answers times out`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("late")
                .headersDelay(1, TimeUnit.SECONDS).build()
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor {
                    it.withReadTimeout(50, TimeUnit.MILLISECONDS)
                        .withConnectTimeout(50, TimeUnit.MILLISECONDS)
                        .proceed(it.request())
                }
            ).build()

        // The downloader's own limits are generous; the interceptor tightens them for the test.
        val result = SubscriptionDownloader(client, Dispatchers.Unconfined, true)
            .fetch(url(), null, null)

        assertEquals(FetchResult.Failed(SubscriptionError.TIMEOUT), result)
    }

    private fun Buffer.asResponseBody() =
        this.readUtf8().toResponseBody("text/calendar".toMediaType()).let { body ->
            // A body with no announced length, so only the cap can notice its size.
            object : okhttp3.ResponseBody() {
                override fun contentType() = body.contentType()

                override fun contentLength() = -1L

                override fun source() = body.source()
            }
        }
}
