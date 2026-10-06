// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote.caldav

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A server answer worth keeping: its code, body and ETag. */
internal data class DavAnswer(val code: Int, val body: String, val etag: String?)

/** Sends WebDAV requests and turns failures into [DavResult] values. Cancellation still propagates. */
internal class DavHttp(
    private val client: OkHttpClient,
    private val server: HttpUrl,
    private val io: CoroutineDispatcher
) {
    fun url(href: String): HttpUrl = requireNotNull(server.resolve(href)) { "Not a URL: $href" }

    suspend fun send(
        method: String,
        href: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        contentType: String = XML
    ): DavResult<DavAnswer> = withContext(io) {
        val request = Request.Builder()
            .url(url(href))
            .method(method, body?.toRequestBody(contentType.toMediaType()))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        execute(request)
    }

    private suspend fun execute(request: Request): DavResult<DavAnswer> = withContext(io) {
        try {
            client.newCall(request).execute().use { response ->
                result(DavAnswer(response.code, response.body.string(), response.header("ETag")))
            }
        } catch (_: SocketTimeoutException) {
            DavResult.NetworkError(DavResult.NetworkError.Kind.TIMEOUT)
        } catch (_: UnknownHostException) {
            DavResult.NetworkError(DavResult.NetworkError.Kind.UNREACHABLE)
        } catch (_: ConnectException) {
            DavResult.NetworkError(DavResult.NetworkError.Kind.UNREACHABLE)
        } catch (_: NoRouteToHostException) {
            DavResult.NetworkError(DavResult.NetworkError.Kind.UNREACHABLE)
        } catch (_: SSLException) {
            DavResult.NetworkError(DavResult.NetworkError.Kind.TLS)
        } catch (_: IOException) {
            DavResult.NetworkError(DavResult.NetworkError.Kind.OTHER)
        }
    }

    private fun result(answer: DavAnswer): DavResult<DavAnswer> = when {
        // RFC 6578 §3.2: an old or unknown token is refused with this precondition.
        answer.code in TOKEN_ERRORS && "valid-sync-token" in answer.body ->
            DavResult.SyncTokenExpired

        answer.code == UNAUTHORIZED -> DavResult.Unauthorized

        answer.code == FORBIDDEN -> DavResult.Forbidden

        answer.code == NOT_FOUND -> DavResult.NotFound

        answer.code == PRECONDITION_FAILED -> DavResult.PreconditionFailed

        answer.code >= FIRST_ERROR -> DavResult.HttpError(answer.code)

        else -> DavResult.Success(answer)
    }

    companion object {
        const val XML = "application/xml; charset=utf-8"
        const val CALENDAR = "text/calendar; charset=utf-8"

        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404
        private const val PRECONDITION_FAILED = 412
        private const val FIRST_ERROR = 400
        private val TOKEN_ERRORS = setOf(FORBIDDEN, 409)
    }
}
