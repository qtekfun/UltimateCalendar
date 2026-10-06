// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync

import kotlinx.coroutines.flow.Flow

private const val QUARTER = 15L
private const val HALF = 30L
private const val HOUR_MINUTES = 60L

/** How often the periodic check runs (RF-06); [minutes] is null when only manual checks run. */
enum class CheckInterval(val minutes: Long?) {
    QUARTER_HOUR(QUARTER),
    HALF_HOUR(HALF),
    HOUR(HOUR_MINUTES),
    MANUAL_ONLY(null)
}

/** What the check reads from Settings (RF-10). */
interface InvitationCheckSettings {
    /** The interval now, and every change after. */
    val intervals: Flow<CheckInterval>

    /** The user's extra e-mail addresses, besides the owner of each calendar. */
    suspend fun aliases(): Set<String>
}
