// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote.caldav

import com.qtekfun.ultimatecalendar.data.auth.SignedInAccount
import com.qtekfun.ultimatecalendar.data.remote.CredentialsProvider
import com.qtekfun.ultimatecalendar.data.remote.ServerUrl
import com.qtekfun.ultimatecalendar.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.Credentials
import okhttp3.OkHttpClient

/** The CalDAV reads and writes of one account. */
class CalDav(val read: CalDavClient, val write: CalDavWrites)

/** Builds the CalDAV client of an account, authenticated with its app password. */
class CalDavProvider @Inject constructor(
    private val http: OkHttpClient,
    private val credentials: CredentialsProvider,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    fun connect(account: SignedInAccount): CalDav? = connect(account, allowInsecure = false)

    /** [allowInsecure] exists only so tests can use a local plain-http server. */
    internal fun connect(account: SignedInAccount, allowInsecure: Boolean): CalDav? {
        val server =
            ServerUrl.parse(account.serverUrl, allowInsecure) as? ServerUrl.ParseResult.Valid
                ?: return null
        val client = http.newBuilder().addInterceptor { chain ->
            val request = chain.request().newBuilder()
            credentials.credentials()?.let {
                request.header(
                    "Authorization",
                    Credentials.basic(it.loginName, it.appPassword, Charsets.UTF_8)
                )
            }
            chain.proceed(request.build())
        }.build()
        return CalDav(
            CalDavClient(client, server.url.root, io),
            CalDavWrites(client, server.url.root, io)
        )
    }
}
