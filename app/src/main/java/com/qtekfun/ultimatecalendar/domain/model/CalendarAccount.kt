// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

/**
 * The account a calendar belongs to: Google, DAVx5, "local"… [type] is the Android account type
 * (`com.google`, `bitfire.at.davdroid`, `LOCAL`).
 */
data class CalendarAccount(val name: String, val type: String) {
    /** The on-device account (`CalendarContract.ACCOUNT_TYPE_LOCAL`): nothing syncs it. */
    val isLocal: Boolean get() = type == LOCAL_TYPE

    /** The app's own CalDAV account (RF-12): not an Android account, the app syncs it itself. */
    val isCalDav: Boolean get() = type == CALDAV_TYPE

    companion object {
        private const val LOCAL_TYPE = "LOCAL"

        const val CALDAV_TYPE = "com.qtekfun.ultimatecalendar.caldav"
    }
}
