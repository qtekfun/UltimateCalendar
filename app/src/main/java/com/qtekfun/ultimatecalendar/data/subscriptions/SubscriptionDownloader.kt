// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

import com.qtekfun.ultimatecalendar.di.IoDispatcher
import com.qtekfun.ultimatecalendar.domain.subscriptions.SubscriptionError
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.time.Duration
import javax.inject.Inject
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** What a download came to. */
sealed interface FetchResult {
    /** A new copy of the calendar, with the validators the next request will send. */
    data class Fresh(val body: String, val etag: String?, val lastModified: String?) : FetchResult

    /** The server says what we have is still current (304). */
    data object NotModified : FetchResult

    data class Failed(val error: SubscriptionError, val httpCode: Int? = null) : FetchResult
}

/**
 * Downloads the text of a subscription. The address is a secret and never logged, and there is
 * no logging interceptor. HTTPS only: a redirect to anything else is refused. No cookies, no
 * credentials, a size cap on what is read (after decompression), timeouts, and a conditional
 * request (`If-None-Match`, `If-Modified-Since`) so an unchanged feed costs almost nothing.
 */
class SubscriptionDownloader internal constructor(
    client: OkHttpClient,
    private val io: CoroutineDispatcher,
    private val allowInsecure: Boolean
) {
    @Inject
    constructor(client: OkHttpClient, @IoDispatcher io: CoroutineDispatcher) :
        this(client, io, allowInsecure = false)

    // Redirects are followed here, one by one, so that each can be checked.
    private val http = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(CONNECT_TIMEOUT)
        .readTimeout(READ_TIMEOUT)
        .callTimeout(CALL_TIMEOUT)
        .build()

    /** Fetches [url]; [etag] and [lastModified] are the validators of the copy we already have. */
    suspend fun fetch(url: String, etag: String?, lastModified: String?): FetchResult =
        withContext(io) {
            val first = url.toHttpUrlOrNull()
            if (first == null || !allowed(first)) {
                FetchResult.Failed(SubscriptionError.INSECURE_REDIRECT)
            } else {
                try {
                    follow(first, etag, lastModified)
                } catch (_: InterruptedIOException) {
                    FetchResult.Failed(SubscriptionError.TIMEOUT)
                } catch (_: UnknownHostException) {
                    FetchResult.Failed(SubscriptionError.UNREACHABLE)
                } catch (_: ConnectException) {
                    FetchResult.Failed(SubscriptionError.UNREACHABLE)
                } catch (_: NoRouteToHostException) {
                    FetchResult.Failed(SubscriptionError.UNREACHABLE)
                } catch (_: SSLException) {
                    FetchResult.Failed(SubscriptionError.TLS)
                } catch (_: IOException) {
                    FetchResult.Failed(SubscriptionError.NETWORK)
                }
            }
        }

    private fun allowed(url: HttpUrl) = url.isHttps || allowInsecure

    private fun follow(start: HttpUrl, etag: String?, lastModified: String?): FetchResult {
        var current = start
        repeat(MAX_REDIRECTS + 1) {
            val request = Request.Builder()
                .url(current)
                .header("Accept", "text/calendar, text/plain;q=0.5, */*;q=0.1")
                .apply {
                    etag?.let { header("If-None-Match", it) }
                    lastModified?.let { header("If-Modified-Since", it) }
                }
                .build()
            http.newCall(request).execute().use { response ->
                val code = response.code
                if (code in REDIRECTS) {
                    val next = response.header("Location")?.let { current.resolve(it) }
                    if (next == null || !allowed(next)) {
                        return FetchResult.Failed(SubscriptionError.INSECURE_REDIRECT)
                    }
                    current = next
                } else {
                    return result(response)
                }
            }
        }
        return FetchResult.Failed(SubscriptionError.TOO_MANY_REDIRECTS)
    }

    private fun result(response: Response): FetchResult = when (response.code) {
        NOT_MODIFIED -> FetchResult.NotModified
        NO_CONTENT -> FetchResult.Failed(SubscriptionError.NOT_CALENDAR)
        !in OK_RANGE -> FetchResult.Failed(SubscriptionError.HTTP, response.code)
        else -> body(response)
    }

    private fun body(response: Response): FetchResult {
        val body = response.body
        if (body.contentLength() > MAX_BYTES) return FetchResult.Failed(SubscriptionError.TOO_LARGE)
        val source = body.source()
        // Asks for one byte more than allowed: if it is there, the feed is too large.
        source.request(MAX_BYTES + 1)
        if (source.buffer.size > MAX_BYTES) return FetchResult.Failed(SubscriptionError.TOO_LARGE)
        return FetchResult.Fresh(
            body = source.readUtf8().removePrefix(BOM),
            etag = response.header("ETag"),
            lastModified = response.header("Last-Modified")
        )
    }

    companion object {
        /** The most a feed may be, decompressed: 10 MB. */
        const val MAX_BYTES = 10L * 1024 * 1024
        const val MAX_REDIRECTS = 5

        private const val BOM = ""
        private const val NOT_MODIFIED = 304
        private const val NO_CONTENT = 204
        private val OK_RANGE = 200..299
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        private val CONNECT_TIMEOUT = Duration.ofSeconds(15)
        private val READ_TIMEOUT = Duration.ofSeconds(30)
        private val CALL_TIMEOUT = Duration.ofSeconds(120)
    }
}
