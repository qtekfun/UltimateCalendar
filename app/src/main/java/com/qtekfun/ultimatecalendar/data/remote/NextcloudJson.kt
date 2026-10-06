// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.remote

import kotlinx.serialization.json.Json

/**
 * JSON settings for Nextcloud endpoints: unknown fields are ignored and nulls fall back to
 * defaults, so newer or older servers still parse.
 */
val NextcloudJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
