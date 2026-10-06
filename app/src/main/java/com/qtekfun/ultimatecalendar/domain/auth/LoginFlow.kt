// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.auth

import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.LoginFlowApi
import com.qtekfun.ultimatecalendar.data.auth.LoginFlowApiFactory
import com.qtekfun.ultimatecalendar.data.auth.dto.LoginResultDto
import com.qtekfun.ultimatecalendar.data.remote.ApiResult
import com.qtekfun.ultimatecalendar.data.remote.Credentials
import com.qtekfun.ultimatecalendar.data.remote.ServerUrl
import com.qtekfun.ultimatecalendar.data.remote.apiCall
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Nextcloud Login Flow v2 (RF-12): checks that the address is a Nextcloud server, waits while the
 * user logs in in the browser and stores the sign-in with its encrypted app password.
 */
class LoginFlow @Inject constructor(
    private val apiFactory: LoginFlowApiFactory,
    private val session: AccountSession
) {
    /** Login tokens are valid for 20 minutes on the server. */
    fun login(
        serverInput: String,
        pollInterval: Duration = 2.seconds,
        timeout: Duration = 20.minutes
    ): Flow<LoginState> = when (val parsed = ServerUrl.parse(serverInput)) {
        is ServerUrl.ParseResult.Valid -> login(parsed.url, pollInterval, timeout)
        ServerUrl.ParseResult.Insecure -> flowOf(LoginState.Failed(LoginError.INSECURE_URL))
        ServerUrl.ParseResult.Invalid -> flowOf(LoginState.Failed(LoginError.INVALID_URL))
    }

    /** The flow for an already validated server; tests use it with a local plain-http server. */
    internal fun login(
        server: ServerUrl,
        pollInterval: Duration,
        timeout: Duration
    ): Flow<LoginState> = flow {
        run(server, pollInterval, timeout)?.let { emit(LoginState.Failed(it)) }
    }

    /** Runs the flow against [server], emitting progress; returns the error if it fails. */
    // Each step can fail and ends the flow there; early returns keep the sequence readable.
    @Suppress("ReturnCount")
    private suspend fun FlowCollector<LoginState>.run(
        server: ServerUrl,
        pollInterval: Duration,
        timeout: Duration
    ): LoginError? {
        emit(LoginState.CheckingServer)
        val api = apiFactory.create(server)
        checkServer(api)?.let { return it }
        val start = when (val result = apiCall { api.startLogin() }) {
            is ApiResult.Success -> result.value
            else -> return errorOf(result)
        }
        emit(LoginState.WaitingForBrowser(start.login))
        val endpoint = securePollEndpoint(start.poll.endpoint, server)
        val login =
            poll(api, endpoint, start.poll.token, pollInterval, timeout)
                ?: return LoginError.EXPIRED
        val credentials = Credentials(login.loginName, login.appPassword)
        val canonical =
            (ServerUrl.parse(login.server) as? ServerUrl.ParseResult.Valid)?.url ?: server
        emit(LoginState.LoggedIn(session.signIn(canonical, credentials)))
        return null
    }

    private suspend fun checkServer(api: LoginFlowApi): LoginError? = when (
        val status = apiCall {
            api.status()
        }
    ) {
        is ApiResult.Success -> if (status.value.installed) null else LoginError.NOT_NEXTCLOUD
        is ApiResult.NetworkError -> errorOf(status)
        else -> LoginError.NOT_NEXTCLOUD
    }

    /** Polls until the user logs in; network hiccups and 404s just mean "not yet". */
    private suspend fun poll(
        api: LoginFlowApi,
        endpoint: String,
        token: String,
        interval: Duration,
        timeout: Duration
    ): LoginResultDto? = withTimeoutOrNull(timeout) {
        var login: LoginResultDto? = null
        while (login == null) {
            login = (apiCall { api.poll(endpoint, token) } as? ApiResult.Success)?.value
            if (login == null) delay(interval)
        }
        login
    }

    private fun errorOf(result: ApiResult<*>): LoginError = when (result) {
        is ApiResult.NetworkError -> when (result.kind) {
            ApiResult.NetworkError.Kind.TLS -> LoginError.TLS_ERROR
            else -> LoginError.UNREACHABLE
        }

        ApiResult.NotFound, ApiResult.ParseError -> LoginError.NOT_NEXTCLOUD

        else -> LoginError.UNKNOWN
    }

    internal companion object {
        /**
         * Servers behind a misconfigured reverse proxy often return an http poll endpoint. Plain
         * http is never used: on the same host it is upgraded to https, elsewhere it is refused
         * (the request then fails like any unreachable endpoint).
         */
        fun securePollEndpoint(endpoint: String, server: ServerUrl): String {
            val url = endpoint.toHttpUrlOrNull() ?: return endpoint
            return if (!url.isHttps && url.host == server.root.host && server.root.isHttps) {
                url.newBuilder().scheme("https").port(server.root.port).build().toString()
            } else {
                endpoint
            }
        }
    }
}
