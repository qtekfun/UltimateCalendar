// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

/** Why a check asks for a sync: it decides how urgent the request is and whether it is limited. */
enum class SyncReason {
    /** The periodic job or the app opening: a normal request the system may batch and delay. */
    BACKGROUND,

    /** The user asked (pull to refresh): expedited and never limited. */
    MANUAL
}
