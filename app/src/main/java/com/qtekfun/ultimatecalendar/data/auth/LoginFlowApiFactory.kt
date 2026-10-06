// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.auth

import com.qtekfun.ultimatecalendar.data.remote.Credentials
import com.qtekfun.ultimatecalendar.data.remote.ServerUrl
import javax.inject.Inject
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Builds the [LoginFlowApi] of a validated (HTTPS) server. */
class LoginFlowApiFactory @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    fun create(server: ServerUrl): LoginFlowApi = Retrofit.Builder()
        .baseUrl(server.root)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(LoginFlowApi::class.java)
}

/** The basic auth header for [credentials]. */
fun basicAuth(credentials: Credentials): String =
    okhttp3.Credentials.basic(credentials.loginName, credentials.appPassword, Charsets.UTF_8)
