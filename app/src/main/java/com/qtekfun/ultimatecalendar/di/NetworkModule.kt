// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import com.qtekfun.ultimatecalendar.data.remote.NextcloudJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Networking shared by every account. There is deliberately no logging interceptor, so
 * requests, credentials and event contents never reach the logs.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val TRANSFER_TIMEOUT_SECONDS = 60L

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .readTimeout(Duration.ofSeconds(TRANSFER_TIMEOUT_SECONDS))
        .writeTimeout(Duration.ofSeconds(TRANSFER_TIMEOUT_SECONDS))
        .build()

    @Provides
    @Singleton
    fun json(): Json = NextcloudJson
}
