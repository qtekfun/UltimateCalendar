// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.sync

import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount

/** How many sync requests the system took and how many it refused. */
data class SyncRequests(val requested: Int, val failed: Int)

/**
 * Asks the sources to fetch what is new before a check looks at them (RF-06). Best-effort: the
 * system or the account's adapter may ignore or limit the request (SPEC §8, verified in T02), so
 * a check never waits for it nor fails because of it. A sync that does bring changes reaches the
 * app through the source's change notifications, which run another check.
 */
interface SourceSyncRequester {
    suspend fun requestSync(accounts: Set<CalendarAccount>): SyncRequests
}
