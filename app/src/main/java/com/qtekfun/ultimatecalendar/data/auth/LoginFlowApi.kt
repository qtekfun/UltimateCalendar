// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.auth

import com.qtekfun.ultimatecalendar.data.auth.dto.LoginResultDto
import com.qtekfun.ultimatecalendar.data.auth.dto.LoginStartDto
import com.qtekfun.ultimatecalendar.data.auth.dto.StatusDto
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Nextcloud Login Flow v2 and the server checks around it. Paths are relative to the server
 * root; the authenticated call takes the basic auth header explicitly.
 */
interface LoginFlowApi {
    @GET("status.php")
    suspend fun status(): Response<StatusDto>

    @POST("index.php/login/v2")
    suspend fun startLogin(): Response<LoginStartDto>

    /** Answers 404 until the user finishes logging in in the browser. */
    @FormUrlEncoded
    @POST
    suspend fun poll(@Url endpoint: String, @Field("token") token: String): Response<LoginResultDto>

    /** Revokes the app password used in [authorization]; used on logout. */
    @Headers("OCS-APIRequest: true")
    @DELETE("ocs/v2.php/core/apppassword")
    suspend fun revokeAppPassword(@Header("Authorization") authorization: String): Response<Unit>
}
