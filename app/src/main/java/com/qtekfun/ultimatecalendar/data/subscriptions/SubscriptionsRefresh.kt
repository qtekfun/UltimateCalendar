// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.subscriptions

/** Downloads every enabled subscription again, in the caller's coroutine. */
fun interface SubscriptionsRefresh {
    suspend fun refreshAll(): RefreshSummary
}
