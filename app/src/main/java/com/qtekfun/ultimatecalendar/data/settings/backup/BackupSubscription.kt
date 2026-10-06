// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings.backup

import kotlinx.serialization.Serializable

/**
 * A subscription in a backup (T39). The [url] may be a secret: it only ever travels inside the
 * sealed content. Everything but the name and address is optional, so a backup from another
 * version still opens; a restore checks every field (HTTPS only, known intervals).
 */
@Serializable
data class BackupSubscription(
    val name: String,
    val url: String,
    val color: Int? = null,
    val enabled: Boolean? = null,
    val refreshHours: Int? = null
)
