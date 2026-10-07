// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

/** Why a check asks for a sync: it decides how urgent the request is and whether it is limited. */
enum class SyncReason {
    /** The periodic job or the app opening: a normal request the system may batch and delay. */
    BACKGROUND,

    /** The user asked (pull to refresh): expedited and never limited. */
    MANUAL,

    /**
     * The app just wrote an event with guests, or the user's answer, into the calendar provider:
     * the account's sync adapter must upload it for the invitation to go out. Expedited and not
     * limited by the background wait, but still not asked when the account has calendar sync off
     * or there is no network, because the request would only be queued by the system.
     */
    WRITE
}
